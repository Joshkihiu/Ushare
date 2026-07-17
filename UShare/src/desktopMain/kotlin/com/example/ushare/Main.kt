package com.example.ushare

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "UShare",
        state = WindowState(width = 420.dp, height = 860.dp)
    ) {
        App()
    }
}
