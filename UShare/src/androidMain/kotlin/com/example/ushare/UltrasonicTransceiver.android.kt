package com.example.ushare

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

actual class UltrasonicTransceiver actual constructor(
    private val callbacks: TransceiverCallbacks
) {
    private val audioMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var backgroundJob: Job? = null
    private var persistentRecorder: AudioRecord? = null
    private val backgroundState = ReceiverState()
    private var lastEchoTimeMs = 0L

    companion object {
        /** Minimum gap between echo transmissions to prevent feedback chirping */
        private const val ECHO_COOLDOWN_MS = 2500L
        /** Minimum message length to consider it a real signal (not noise) */
        private const val MIN_MSG_LENGTH = 4
    }

    actual fun startBackgroundReceiver() {
        if (backgroundJob?.isActive == true) return
        UltrasonicLog.d("Background receiver starting")
        val minBuffer = AudioRecord.getMinBufferSize(
            UltrasonicConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(SignalAnalyzer.hopSize * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            UltrasonicConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            minBuffer
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            UltrasonicLog.e("Persistent AudioRecord failed to initialize")
            recorder.release()
            return
        }
        persistentRecorder = recorder
        recorder.startRecording()
        backgroundJob = scope.launch { runContinuousRead(recorder) }
    }

    actual fun stopBackgroundReceiver() {
        UltrasonicLog.d("Background receiver stopping")
        backgroundJob?.cancel()
        backgroundJob = null
        try { persistentRecorder?.stop() } catch (_: Exception) {}
        persistentRecorder?.release()
        persistentRecorder = null
        backgroundState.reset()
    }

    actual suspend fun quickSend(text: String) {
        withAudioSession {
            UltrasonicLog.d("Quick send: '$text'")
            transmit(text)
        }
    }

    actual suspend fun manualSend(text: String) {
        withAudioSession {
            UltrasonicLog.d("Manual send: '$text'")
            transmit(text)
        }
    }

    actual suspend fun confirmedSend(text: String, onPhase: (SendPhase) -> Unit): Boolean =
        withAudioSession {
            var attempt = 1
            while (coroutineContext.isActive) {
                onPhase(SendPhase.TRANSMITTING)
                UltrasonicLog.d("Confirmed send attempt $attempt: transmitting '$text'")
                transmit(text)

                onPhase(SendPhase.WAITING_ECHO)
                UltrasonicLog.d("Listening for echo bounce (timeout ${UltrasonicConfig.ECHO_TIMEOUT_SEC}s)")
                val echoState = ReceiverState()
                val echo = listenOnce(echoState, (UltrasonicConfig.ECHO_TIMEOUT_SEC * 1000).toLong())

                if (echo == text) {
                    UltrasonicLog.d("Echo verified: '$echo' — sending ACK")
                    transmit("${UltrasonicConfig.ACK_PREFIX}$text")
                    onPhase(SendPhase.CONFIRMED)
                    return@withAudioSession true
                }

                UltrasonicLog.d("Mismatch/timeout on attempt $attempt: got '$echo'")
                attempt++
                delay((UltrasonicConfig.RETRY_GAP_SEC * 1000).toLong())
            }
            onPhase(SendPhase.FAILED)
            false
        }

    private suspend fun <T> withAudioSession(block: suspend () -> T): T {
        stopBackgroundReceiver()
        delay(80)
        return try {
            audioMutex.withLock { block() }
        } finally {
            startBackgroundReceiver()
        }
    }

    /** Continuous background read from a persistent AudioRecord — no start/stop cycling */
    private suspend fun runContinuousRead(recorder: AudioRecord) {
        UltrasonicLog.d("Continuous receiver active — mic stays on steadily")
        val hopBuffer = FloatArray(SignalAnalyzer.hopSize)
        while (coroutineContext.isActive) {
            try {
                val read = recorder.read(hopBuffer, 0, hopBuffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                audioMutex.withLock {
                    val msg = backgroundState.processHop(hopBuffer.copyOf(read))
                    if (msg != null) {
                        handleIncoming(msg)
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                break
            } catch (e: Exception) {
                UltrasonicLog.e("Continuous read error", e)
                delay(200)
            }
        }
    }

    private fun handleIncoming(msg: String) {
        // ACK messages — round-trip confirmation from the other device
        if (msg.startsWith(UltrasonicConfig.ACK_PREFIX)) {
            val confirmed = msg.removePrefix(UltrasonicConfig.ACK_PREFIX)
            UltrasonicLog.d("SUCCESS: round-trip confirmed for '$confirmed'")
            callbacks.onIncomingConfirmed(confirmed)
            return
        }

        // Raw message received from another device.
        if (msg.length < MIN_MSG_LENGTH) {
            UltrasonicLog.d("Ignored short message '$msg' (likely noise)")
            return
        }

        // Show in received log immediately
        callbacks.onIncomingConfirmed(msg)

        // Echo back for verification — launch async so the receiver loop
        // keeps reading audio (doesn't miss the sender's ACK).
        val now = System.currentTimeMillis()
        if (now - lastEchoTimeMs >= ECHO_COOLDOWN_MS) {
            lastEchoTimeMs = now
            UltrasonicLog.d("Echoing '$msg' back for verification")
            scope.launch {
                audioMutex.withLock {
                    transmit(msg)
                }
            }
        } else {
            UltrasonicLog.d("Echo suppressed (cooldown) for '$msg'")
        }
    }

    private suspend fun transmit(text: String) = withContext(Dispatchers.IO) {
        val audio = SignalGenerator.generate(text)
        val minBuffer = AudioTrack.getMinBufferSize(
            UltrasonicConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(audio.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(UltrasonicConfig.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(minBuffer)
            .build()

        try {
            track.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            val durationMs = (audio.size.toDouble() / UltrasonicConfig.SAMPLE_RATE * 1000).toLong() + 100
            delay(durationMs)
            track.stop()
        } catch (e: Exception) {
            UltrasonicLog.e("Hardware error on playback", e)
        } finally {
            track.release()
        }
    }

    /** Temporary listen used during confirmedSend echo-wait (uses its own fresh AudioRecord) */
    private suspend fun listenOnce(state: ReceiverState, timeoutMs: Long): String? =
        withContext(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                UltrasonicConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(SignalAnalyzer.hopSize * 4)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                UltrasonicConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                minBuffer
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext null
            }

            val hopBuffer = FloatArray(SignalAnalyzer.hopSize)
            val deadline = System.currentTimeMillis() + timeoutMs

            try {
                recorder.startRecording()
                while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
                    val read = recorder.read(hopBuffer, 0, hopBuffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue
                    val result = state.processHop(hopBuffer.copyOf(read))
                    if (result != null) {
                        UltrasonicLog.d("ListenOnce — RECEIVED RAW: $result")
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                UltrasonicLog.e("ListenOnce error", e)
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
            null
        }

    private class ReceiverState {
        private val receivedChars = mutableListOf<Char>()
        private var lastGridPair: Pair<Int, Int>? = null
        private var silentCycles = 0
        private var rollingBuffer = FloatArray(0)

        fun reset() {
            receivedChars.clear()
            lastGridPair = null
            silentCycles = 0
            rollingBuffer = FloatArray(0)
        }

        fun processHop(hop: FloatArray): String? {
            rollingBuffer = if (rollingBuffer.isEmpty()) hop else rollingBuffer + hop
            if (rollingBuffer.size < SignalAnalyzer.blockSize) return null
            if (rollingBuffer.size > SignalAnalyzer.blockSize) {
                rollingBuffer = rollingBuffer.copyOfRange(
                    rollingBuffer.size - SignalAnalyzer.blockSize,
                    rollingBuffer.size
                )
            }

            val matched = SignalAnalyzer.analyzeBlock(rollingBuffer)
            if (matched != null) {
                if (silentCycles > 0) lastGridPair = null
                if (matched != lastGridPair) {
                    val ascii = FrequencyGrid.reverse[matched] ?: -1
                    if (ascii >= 32 || ascii in listOf(9, 10, 13)) {
                        receivedChars.add(ascii.toChar())
                    }
                    lastGridPair = matched
                }
                silentCycles = 0
            } else {
                silentCycles++
                if (receivedChars.isNotEmpty() && silentCycles > 5) {
                    val raw = receivedChars.toList()
                    receivedChars.clear()
                    lastGridPair = null
                    silentCycles = 0
                    if (raw.isNotEmpty()) {
                        return calculateMostCommon(raw)
                    }
                }
            }
            return null
        }
    }
}
