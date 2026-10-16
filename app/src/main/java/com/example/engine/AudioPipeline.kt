package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import com.example.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sin

enum class AudioDestination {
    WAKE_WORD,
    LIVE_SESSION,
    MUTED
}

class AudioPipeline(
    private val context: Context,
    private val wakeWordEngine: WakeWordEngine
) {
    companion object {
        private const val TAG = "AudioPipeline"
        const val SAMPLE_RATE_IN = 16000
        const val SAMPLE_RATE_OUT = 24000
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var isRecording = false

    @Volatile
    var destination: AudioDestination = AudioDestination.WAKE_WORD

    // Live audio frame flow for Gemini WebSocket: base64 encoded audio/pcm;rate=16000
    private val _liveAudioChunks = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val liveAudioChunks = _liveAudioChunks.asSharedFlow()

    // Output audio level for UI visualizer (0.0 to 1.0)
    private val _inputAudioLevel = MutableStateFlow(0f)
    val inputAudioLevel = _inputAudioLevel.asStateFlow()

    private val _outputAudioLevel = MutableStateFlow(0f)
    val outputAudioLevel = _outputAudioLevel.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordThread: Thread? = null

    // Audio playback
    private var audioTrack: AudioTrack? = null
    private val audioPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var isPlaying = false
    private var playbackThread: Thread? = null

    // Wake word trigger callback
    var onWakeWordDetected: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return
        isRecording = true

        initAudioTrack()
        startPlaybackWorker()

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize * BUFFER_SIZE_FACTOR, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            }

            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}")
            isRecording = false
            return
        }

        recordThread = Thread({
            val audioBuffer = ShortArray(1024)
            val byteBuffer = ByteBuffer.allocate(audioBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            while (isRecording && audioRecord != null) {
                val readShorts = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                if (readShorts > 0) {
                    // Compute amplitude for UI
                    var sum = 0.0
                    for (i in 0 until readShorts) {
                        sum += Math.abs(audioBuffer[i].toInt())
                    }
                    val avgLevel = (sum / readShorts / 16000.0).toFloat().coerceIn(0f, 1f)
                    _inputAudioLevel.value = avgLevel

                    when (destination) {
                        AudioDestination.WAKE_WORD -> {
                            val detected = wakeWordEngine.processPcmFrame(audioBuffer, readShorts)
                            if (detected) {
                                scope.launch(Dispatchers.Main) {
                                    playChime()
                                    onWakeWordDetected?.invoke()
                                }
                            }
                        }
                        AudioDestination.LIVE_SESSION -> {
                            byteBuffer.clear()
                            for (i in 0 until readShorts) {
                                byteBuffer.putShort(audioBuffer[i])
                            }
                            val base64Data = Base64.encodeToString(
                                byteBuffer.array(),
                                0,
                                readShorts * 2,
                                Base64.NO_WRAP
                            )
                            _liveAudioChunks.tryEmit(base64Data)
                        }
                        AudioDestination.MUTED -> {
                            // Do nothing
                        }
                    }
                }
            }
        }, "MayaAudioRecordThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_OUT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufferSize * 2, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    private fun startPlaybackWorker() {
        isPlaying = true
        playbackThread = Thread({
            while (isPlaying) {
                val chunk = audioPlaybackQueue.poll()
                if (chunk != null && audioTrack != null) {
                    audioTrack?.write(chunk, 0, chunk.size)

                    // Estimate output audio level
                    var sum = 0.0
                    val shortCount = chunk.size / 2
                    for (i in 0 until shortCount) {
                        val sample = (chunk[i * 2 + 1].toInt() shl 8) or (chunk[i * 2].toInt() and 0xFF)
                        sum += Math.abs(sample)
                    }
                    _outputAudioLevel.value = (sum / shortCount / 14000.0).toFloat().coerceIn(0f, 1f)
                } else {
                    _outputAudioLevel.value = 0f
                    try {
                        Thread.sleep(15)
                    } catch (_: InterruptedException) { }
                }
            }
        }, "MayaAudioPlaybackThread").apply {
            start()
        }
    }

    fun enqueuePlaybackPcm(pcmBytes: ByteArray) {
        audioPlaybackQueue.offer(pcmBytes)
    }

    fun flushPlayback() {
        // Barge-in: immediately clear audio buffer and reset AudioTrack
        audioPlaybackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack: ${e.message}")
        }
        _outputAudioLevel.value = 0f
    }

    fun playChime() {
        // Synthesize an uplifting 2-tone futuristic chime (C5 523Hz -> G5 784Hz)
        scope.launch(Dispatchers.IO) {
            val sampleRate = SAMPLE_RATE_OUT
            val durationMs = 280
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val chimePcm = ByteArray(numSamples * 2)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = if (i < numSamples / 2) 523.25 else 783.99
                val decay = 1.0 - (i.toDouble() / numSamples)
                val sample = (sin(2.0 * Math.PI * freq * t) * 12000 * decay).toInt().toShort()

                chimePcm[i * 2] = (sample.toInt() and 0xFF).toByte()
                chimePcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            enqueuePlaybackPcm(chimePcm)
        }
    }

    fun stop() {
        isRecording = false
        isPlaying = false

        try {
            recordThread?.interrupt()
            recordThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null

            playbackThread?.interrupt()
            playbackThread = null
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            audioPlaybackQueue.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioPipeline: ${e.message}")
        }
    }
}
