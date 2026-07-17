package com.example.ushare

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Matches [Fronted.html] playBeep(): 800 Hz sine, peak gain 0.3, 200 ms with
 * exponential fade to 0.001 (Web Audio exponentialRampToValueAtTime).
 */
object NotificationBeep {
    const val SAMPLE_RATE = 44100
    const val FREQUENCY_HZ = 800.0
    const val DURATION_SEC = 0.2
    const val PEAK_GAIN = 0.3
    const val FLOOR_GAIN = 0.001

    fun generateSamples(): FloatArray {
        val count = (SAMPLE_RATE * DURATION_SEC).toInt().coerceAtLeast(1)
        val fadeRatio = ln(FLOOR_GAIN / PEAK_GAIN) / DURATION_SEC
        return FloatArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val gain = PEAK_GAIN * exp(fadeRatio * t)
            (sin(2.0 * PI * FREQUENCY_HZ * t) * gain).toFloat()
        }
    }
}
