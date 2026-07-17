package com.example.ushare

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

actual fun playBeep() {
    thread(name = "notification-beep", isDaemon = true) {
        try {
            val samples = NotificationBeep.generateSamples()
            val format = AudioFormat(
                NotificationBeep.SAMPLE_RATE.toFloat(),
                16,
                1,
                true,
                false
            )
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format)
            line.start()

            val pcm = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            line.write(
                pcm.flatMap { short ->
                    listOf((short.toInt() and 0xFF).toByte(), (short.toInt() shr 8 and 0xFF).toByte())
                }.toByteArray(),
                0,
                pcm.size * 2
            )
            line.drain()
            line.stop()
            line.close()
        } catch (_: Throwable) {
            // Ignore audio failures on desktop builds.
        }
    }
}
