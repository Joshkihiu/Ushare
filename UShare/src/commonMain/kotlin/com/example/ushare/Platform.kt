package com.example.ushare

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

expect fun copyToClipboardPlatform(text: String)

expect fun currentTimeShort(): String

expect fun decodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap?

@Composable
expect fun rememberImagePickerLauncher(onImagePicked: (ByteArray?) -> Unit): () -> Unit
