package com.example.ushare

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

actual fun playBeep() {
    thread(name = "notification-beep", isDaemon = true) {
        try {
            val samples = NotificationBeep.generateSamples()
            val minBuffer = AudioTrack.getMinBufferSize(
                NotificationBeep.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(samples.size * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(NotificationBeep.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(minBuffer)
                .build()

            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            Thread.sleep((NotificationBeep.DURATION_SEC * 1000).toLong() + 50)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Silent fallback — same as HTML mockup.
        }
    }
}
