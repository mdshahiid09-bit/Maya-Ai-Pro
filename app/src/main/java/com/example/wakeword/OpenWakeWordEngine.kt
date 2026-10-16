package com.example.wakeword

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class OpenWakeWordEngine(
    private val context: Context,
    private var targetPhrase: String = "Hey Maya"
) : WakeWordEngine {

    companion object {
        private const val TAG = "OpenWakeWordEngine"
        private const val MODEL_MAYA_ASSET = "hey_maya.tflite"
        private const val MODEL_JARVIS_ASSET = "hey_jarvis.tflite"

        // Audio window parameters: 80ms chunks (1280 samples at 16kHz)
        private const val WINDOW_SAMPLES = 1280
        private const val NUM_FEATURES = 32
    }

    private var interpreter: Interpreter? = null
    private var sensitivity: Float = 0.5f // 0.0 to 1.0
    private var isTfliteLoaded: Boolean = false
    private var isReady: Boolean = false

    // Fallback engine in case TFLite model is not fully trained or fails initialization
    private val acousticFallback = AcousticWakeWordEngine(context, targetPhrase)

    // Audio sliding buffer
    private val audioWindow = FloatArray(WINDOW_SAMPLES)
    private var windowIndex = 0
    private var lastTriggerTime = 0L

    // Reusable TFLite input/output buffers
    private var inputBuffer: ByteBuffer? = null
    private var outputScores = Array(1) { FloatArray(1) }

    override fun initialize(): Boolean {
        acousticFallback.initialize()
        val loaded = tryLoadTfliteModel()
        isReady = true
        return loaded || acousticFallback.isReady()
    }

    private fun tryLoadTfliteModel(): Boolean {
        val modelFileName = if (targetPhrase.equals("Hey Jarvis", ignoreCase = true)) {
            MODEL_JARVIS_ASSET
        } else {
            MODEL_MAYA_ASSET
        }

        return try {
            val byteBuffer = loadModelFile(modelFileName)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            val interp = Interpreter(byteBuffer, options)
            interpreter = interp
            isTfliteLoaded = true

            // Allocate input ByteBuffer (1 x WINDOW_SAMPLES x 4 bytes per float)
            inputBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SAMPLES * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            Log.i(TAG, "Successfully loaded openWakeWord TFLite model: $modelFileName")
            true
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model $modelFileName not initialized: ${e.message}. Using high-precision acoustic fallback.")
            isTfliteLoaded = false
            interpreter = null
            false
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        acousticFallback.setSensitivity(sensitivity)
    }

    override fun setPhrase(phrase: String) {
        if (!this.targetPhrase.equals(phrase, ignoreCase = true)) {
            this.targetPhrase = phrase
            acousticFallback.setPhrase(phrase)
            releaseInterpreter()
            tryLoadTfliteModel()
        }
    }

    override fun isReady(): Boolean = isReady

    fun isTfliteActive(): Boolean = isTfliteLoaded && interpreter != null

    override fun processPcmFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (!isReady || readSize <= 0) return false

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2500L) {
            return false // Debounce refractory period
        }

        // If TFLite is loaded, attempt neural inference
        if (isTfliteLoaded && interpreter != null) {
            try {
                for (i in 0 until readSize) {
                    audioWindow[windowIndex % WINDOW_SAMPLES] = buffer[i] / 32768.0f
                    windowIndex++

                    if (windowIndex % 320 == 0) { // Evaluate every 20ms
                        val inBuf = inputBuffer ?: return false
                        inBuf.rewind()
                        for (s in audioWindow) {
                            inBuf.putFloat(s)
                        }

                        outputScores[0][0] = 0f
                        interpreter?.run(inBuf, outputScores)

                        val score = outputScores[0][0]
                        val threshold = 1.0f - (sensitivity * 0.75f)
                        if (score >= threshold) {
                            lastTriggerTime = now
                            Log.i(TAG, "Wake word '$targetPhrase' detected by openWakeWord TFLite (score: $score)")
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                // If model execution fails, fallback smoothly
                Log.w(TAG, "TFLite inference fallback: ${e.message}")
            }
        }

        // Run acoustic heuristic fallback
        val detected = acousticFallback.processPcmFrame(buffer, readSize)
        if (detected) {
            lastTriggerTime = now
            Log.i(TAG, "Wake word '$targetPhrase' detected by acoustic engine.")
            return true
        }

        return false
    }

    private fun releaseInterpreter() {
        try {
            interpreter?.close()
        } catch (_: Exception) { }
        interpreter = null
        isTfliteLoaded = false
    }

    override fun release() {
        isReady = false
        releaseInterpreter()
        acousticFallback.release()
    }
}
