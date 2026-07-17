package com.example.ushare

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

expect fun copyToClipboardPlatform(text: String)

expect fun currentTimeShort(): String

expect fun decodeByteArrayToImageBitmap(bytes: ByteArray): ImageBitmap?

expect fun saveUserData(key: String, value: String)

expect fun loadUserData(key: String): String?

@Composable
expect fun rememberImagePickerLauncher(onImagePicked: (ByteArray?) -> Unit): () -> Unit

// Keys for saved data
object SavedKeys {
    const val PROFILES = "profiles"
    const val USER_NAME = "user_name"
    const val NEXT_ID = "next_id"
}

/** Serialize profiles to a simple line-based format */
fun serializeProfiles(profiles: List<Profile>): String =
    profiles.joinToString("\n") { "${it.id}|${it.number}|${it.type.name}" }

/** Deserialize profiles from the line-based format */
fun deserializeProfiles(data: String): List<Profile>? {
    val lines = data.trim().lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    return try {
        lines.map { line ->
            val parts = line.split("|")
            Profile(parts[0].toInt(), parts[1], ProfileType.valueOf(parts[2]))
        }
    } catch (e: Exception) { null }
}
