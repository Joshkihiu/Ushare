package com.example.ushare

actual object UltrasonicLog {
    actual fun d(message: String) {
        System.err.println("[UShare/Ultrasonic] $message")
    }

    actual fun e(message: String, throwable: Throwable?) {
        System.err.println("[UShare/Ultrasonic] ERROR: $message")
        throwable?.printStackTrace()
    }
}
