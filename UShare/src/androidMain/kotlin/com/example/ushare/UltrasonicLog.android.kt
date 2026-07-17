package com.example.ushare

import android.util.Log

actual object UltrasonicLog {
    private const val TAG = "UShare/Ultrasonic"

    actual fun d(message: String) {
        Log.d(TAG, message)
    }

    actual fun e(message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
