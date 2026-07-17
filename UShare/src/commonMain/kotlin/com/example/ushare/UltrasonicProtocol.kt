package com.example.ushare

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object UltrasonicConfig {
    const val SAMPLE_RATE = 44100
    const val TONE_DURATION = 0.024
    const val GAP_DURATION = 0.005
    const val PHASE_SHIFT_OFFSET = 300
    const val SNR_THRESHOLD = 3.2
    const val ACK_PREFIX = "#"
    const val ROUND_GAP_SEC = 0.020
    const val PRE_TX_DELAY_SEC = 0.3
    const val RETRY_GAP_SEC = 0.4
    const val ECHO_TIMEOUT_SEC = 4.5

    val LOW_ROW_FREQS = intArrayOf(15000, 15200, 15400, 15600, 15800, 16000, 16200, 16400)
    val HIGH_COL_FREQS = intArrayOf(17500, 17700, 17900, 18100, 18300, 18500, 18700, 18900)
}

object FrequencyGrid {
    val grid: Map<Int, Pair<Int, Int>> = buildMap {
        var idx = 0
        for (r in UltrasonicConfig.LOW_ROW_FREQS) {
            for (c in UltrasonicConfig.HIGH_COL_FREQS) {
                if (idx < 128) {
                    put(idx, r to c)
                    idx++
                }
            }
        }
    }

    val reverse: Map<Pair<Int, Int>, Int> = buildMap {
        grid.forEach { (ascii, pair) ->
            put(pair, ascii)
            put(pair.first to (pair.second + UltrasonicConfig.PHASE_SHIFT_OFFSET), ascii)
        }
    }
}

fun findClosestGridTones(peakLow: Double, peakHigh: Double): Pair<Int, Int>? {
    val closestLow = UltrasonicConfig.LOW_ROW_FREQS.minByOrNull { abs(it - peakLow) } ?: return null
    val possibleHighs = UltrasonicConfig.HIGH_COL_FREQS.flatMap { listOf(it, it + UltrasonicConfig.PHASE_SHIFT_OFFSET) }
    val closestHigh = possibleHighs.minByOrNull { abs(it - peakHigh) } ?: return null
    return if (abs(closestLow - peakLow) < 40 && abs(closestHigh - peakHigh) < 40) {
        closestLow to closestHigh
    } else {
        null
    }
}

fun calculateMostCommon(rawChars: List<Char>): String {
    val rawString = rawChars.joinToString("")
    val chunks = rawString.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
    if (chunks.isEmpty()) return ""
    val counts = chunks.groupingBy { it }.eachCount()
    val (best, freq) = counts.maxByOrNull { it.value }?.toPair() ?: return ""
    return if (freq >= 2 && best.length >= 4) best else ""
}

object SignalGenerator {
    private val toneSamples = (UltrasonicConfig.SAMPLE_RATE * UltrasonicConfig.TONE_DURATION).roundToInt()
    private val gapSamples = (UltrasonicConfig.SAMPLE_RATE * UltrasonicConfig.GAP_DURATION).roundToInt()
    private val roundGapSamples = (UltrasonicConfig.SAMPLE_RATE * UltrasonicConfig.ROUND_GAP_SEC).roundToInt()
    private val preDelaySamples = (UltrasonicConfig.SAMPLE_RATE * UltrasonicConfig.PRE_TX_DELAY_SEC).roundToInt()

    /** Volume multiplier 0.1–1.0, adjusted from Settings → US Volume slider */
    var volume: Float = 1.0f

    fun generate(text: String): FloatArray {
        val segments = mutableListOf<FloatArray>()
        segments += FloatArray(preDelaySamples)

        repeat(3) { round ->
            var phaseInverted = false
            for (ch in text) {
                val ascii = ch.code
                if (ascii >= 128) continue
                val (low, high) = FrequencyGrid.grid[ascii] ?: continue
                segments += tone(low, if (phaseInverted) high + UltrasonicConfig.PHASE_SHIFT_OFFSET else high)
                segments += FloatArray(gapSamples)
                phaseInverted = !phaseInverted
            }
            val space = ' '.code
            val (sLow, sHigh) = FrequencyGrid.grid[space] ?: (0 to 0)
            segments += tone(sLow, if (phaseInverted) sHigh + UltrasonicConfig.PHASE_SHIFT_OFFSET else sHigh)
            segments += FloatArray(gapSamples)
            if (round < 2) segments += FloatArray(roundGapSamples)
        }
        segments += FloatArray(2048)
        return segments.reduce { acc, arr -> acc + arr }
    }

    private fun tone(freqLow: Int, freqHigh: Int): FloatArray {
        val n = toneSamples
        val window = hanning(n)
        val vol = volume.coerceIn(0.1f, 1.0f)
        return FloatArray(n) { i ->
            val t = i.toDouble() / UltrasonicConfig.SAMPLE_RATE
            val sample = (sin(2 * PI * freqLow * t) + sin(2 * PI * freqHigh * t)) * 0.5 * window[i] * vol
            sample.toFloat()
        }
    }

    private fun hanning(size: Int): DoubleArray =
        DoubleArray(size) { i -> 0.5 * (1 - cos(2 * PI * i / (size - 1).coerceAtLeast(1))) }
}

object SignalAnalyzer {
    val blockSize = (UltrasonicConfig.SAMPLE_RATE * UltrasonicConfig.TONE_DURATION).roundToInt()
    val hopSize = (blockSize / 2).coerceAtLeast(1)

    // Noise probe frequencies — midpoints between tone freqs, so they measure
    // off-tone energy (true noise floor) instead of re-reading the same tones.
    private val noiseProbeLow = intArrayOf(15100, 15300, 15500, 15700, 15900, 16100, 16300)
    private val noiseProbeHigh = intArrayOf(17600, 17800, 18000, 18200, 18400, 18600, 18800)

    fun goertzelMagnitude(samples: FloatArray, targetFreq: Int): Float {
        if (samples.isEmpty()) return 0f
        val k = (0.5 + samples.size * targetFreq.toDouble() / UltrasonicConfig.SAMPLE_RATE).roundToInt()
        val w = 2.0 * PI * k / samples.size
        val coeff = 2.0 * cos(w)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (sample in samples) {
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cos(w)
        val imag = q2 * sin(w)
        return sqrt(real * real + imag * imag).toFloat()
    }

    fun analyzeBlock(block: FloatArray): Pair<Int, Int>? {
        var maxLow = 0f
        var peakLow = 0
        for (freq in UltrasonicConfig.LOW_ROW_FREQS) {
            val mag = goertzelMagnitude(block, freq)
            if (mag > maxLow) {
                maxLow = mag
                peakLow = freq
            }
        }

        val highCandidates = UltrasonicConfig.HIGH_COL_FREQS.flatMap { listOf(it, it + UltrasonicConfig.PHASE_SHIFT_OFFSET) }
        var maxHigh = 0f
        var peakHigh = 0
        for (freq in highCandidates) {
            val mag = goertzelMagnitude(block, freq)
            if (mag > maxHigh) {
                maxHigh = mag
                peakHigh = freq
            }
        }

        // Noise floor from OFF-TONE frequencies (midpoints) — more accurate
        var lowNoise = 0f
        for (freq in noiseProbeLow) {
            lowNoise += goertzelMagnitude(block, freq)
        }
        lowNoise /= noiseProbeLow.size.coerceAtLeast(1)

        var highNoise = 0f
        for (freq in noiseProbeHigh) {
            highNoise += goertzelMagnitude(block, freq)
        }
        highNoise /= noiseProbeHigh.size.coerceAtLeast(1)

        if (maxLow <= lowNoise * UltrasonicConfig.SNR_THRESHOLD ||
            maxHigh <= highNoise * UltrasonicConfig.SNR_THRESHOLD
        ) {
            return null
        }
        return findClosestGridTones(peakLow.toDouble(), peakHigh.toDouble())
    }
}

enum class SendPhase {
    TRANSMITTING,
    WAITING_ECHO,
    CONFIRMED,
    FAILED
}

interface TransceiverCallbacks {
    fun onIncomingConfirmed(number: String)
}

expect class UltrasonicTransceiver(callbacks: TransceiverCallbacks) {
    fun startBackgroundReceiver()
    fun stopBackgroundReceiver()
    suspend fun quickSend(text: String)
    suspend fun confirmedSend(text: String, onPhase: (SendPhase) -> Unit): Boolean
    suspend fun manualSend(text: String)
}
