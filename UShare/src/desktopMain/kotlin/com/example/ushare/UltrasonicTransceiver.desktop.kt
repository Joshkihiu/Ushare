package com.example.ushare

import kotlinx.coroutines.delay

actual class UltrasonicTransceiver actual constructor(
    private val callbacks: TransceiverCallbacks
) {
    actual fun startBackgroundReceiver() {
        UltrasonicLog.d("Desktop stub: background receiver (no hardware)")
    }

    actual fun stopBackgroundReceiver() {
        UltrasonicLog.d("Desktop stub: receiver stopped")
    }

    actual suspend fun quickSend(text: String) {
        UltrasonicLog.d("Desktop stub quick send: '$text'")
        delay(300)
    }

    actual suspend fun manualSend(text: String) {
        UltrasonicLog.d("Desktop stub manual send: '$text'")
        delay(300)
    }

    actual suspend fun confirmedSend(text: String, onPhase: (SendPhase) -> Unit): Boolean {
        UltrasonicLog.d("Desktop stub confirmed send: '$text'")
        onPhase(SendPhase.TRANSMITTING)
        delay(400)
        onPhase(SendPhase.WAITING_ECHO)
        delay(800)
        onPhase(SendPhase.CONFIRMED)
        callbacks.onIncomingConfirmed(text)
        return true
    }
}
