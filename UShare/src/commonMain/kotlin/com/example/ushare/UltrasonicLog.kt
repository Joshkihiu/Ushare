package com.example.ushare

expect object UltrasonicLog {
    fun d(message: String)
    fun e(message: String, throwable: Throwable? = null)
}
