package com.example.ushare

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun copyToClipboardPlatform(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}

actual fun currentTimeShort(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
