package com.example.wakeword

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

interface WakeWordEngine {
    fun initialize(): Boolean
    fun processPcmFrame(buffer: ShortArray, readSize: Int): Boolean
    fun setSensitivity(sensitivity: Float)
    fun setPhrase(phrase: String)
    fun release()
    fun isReady(): Boolean
}

class AcousticWakeWordEngine(
    private val context: Context,
    private var targetPhrase: String = "Hey Maya"
) : WakeWordEngine {

    private var sensitivity: Float = 0.5f // 0.0 to 1.0
    private var ready: Boolean = false

    // State machine for phoneme envelope tracking
    private var energyHistory = FloatArray(16)
    private var historyIndex = 0
    private var detectedSyllableCount = 0
    private var lastDetectionTime = 0L
    private var speechStartTimestamp = 0L

    override fun initialize(): Boolean {
        ready = true
        return true
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0.1f, 1.0f)
    }

    override fun setPhrase(phrase: String) {
        this.targetPhrase = phrase
    }

    override fun isReady(): Boolean = ready

    override fun processPcmFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (!ready || readSize <= 0) return false

        // Compute RMS energy of this audio slice
        var sumSquares = 0.0
        var zeroCrossings = 0
        for (i in 0 until readSize) {
            val sample = buffer[i].toInt()
            sumSquares += (sample * sample)
            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                zeroCrossings++
            }
        }
        val rms = sqrt(sumSquares / readSize).toFloat()
        val zcr = zeroCrossings.toFloat() / readSize

        energyHistory[historyIndex % energyHistory.size] = rms
        historyIndex++

        val baselineThreshold = 650f * (1.5f - (sensitivity * 0.8f))
        val now = System.currentTimeMillis()

        // Speech burst detection
        if (rms > baselineThreshold && zcr in 0.02f..0.45f) {
            if (speechStartTimestamp == 0L) {
                speechStartTimestamp = now
                detectedSyllableCount = 1
            } else if (now - speechStartTimestamp in 180..950) {
                detectedSyllableCount++
            }
        } else if (rms < baselineThreshold * 0.4f) {
            // Silence dip
            if (detectedSyllableCount in 2..5 && (now - speechStartTimestamp) in 350..1200) {
                // Potential wake phrase envelope match
                if (now - lastDetectionTime > 3000L) {
                    lastDetectionTime = now
                    detectedSyllableCount = 0
                    speechStartTimestamp = 0L
                    return true
                }
            }
            if (now - speechStartTimestamp > 1400L) {
                detectedSyllableCount = 0
                speechStartTimestamp = 0L
            }
        }

        return false
    }

    override fun release() {
        ready = false
    }
}
