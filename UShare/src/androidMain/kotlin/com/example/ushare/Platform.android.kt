package com.example.ushare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Set by MainActivity.onCreate() before the first composition. */
lateinit var appContext: Context

actual fun copyToClipboardPlatform(text: String) {
    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("number", text))
}

actual fun currentTimeShort(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

actual fun saveUserData(key: String, value: String) {
    val prefs = appContext.getSharedPreferences("ushare_data", Context.MODE_PRIVATE)
    prefs.edit().putString(key, value).apply()
}

actual fun loadUserData(key: String): String? {
    val prefs = appContext.getSharedPreferences("ushare_data", Context.MODE_PRIVATE)
    return prefs.getString(key, null)
}

actual fun encodeBase64(bytes: ByteArray): String =
    java.util.Base64.getEncoder().encodeToString(bytes)

actual fun decodeBase64(str: String): ByteArray? = try {
    java.util.Base64.getDecoder().decode(str)
} catch (e: Exception) { null }

actual fun decodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
actual fun rememberImagePickerLauncher(onImagePicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                onImagePicked(bytes)
            } catch (e: Exception) {
                onImagePicked(null)
            }
        } else {
            onImagePicked(null)
        }
    }
    return { launcher.launch("image/*") }
}
