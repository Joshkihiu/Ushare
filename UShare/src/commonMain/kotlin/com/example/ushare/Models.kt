package com.example.ushare

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

enum class ProfileType(val label: String, val icon: ImageVector) {
    SEND_MONEY("Send Money", Icons.Default.AccountBalanceWallet),
    TILL("Till", Icons.Default.Storefront),
    PAYBILL("Paybill", Icons.Default.Payments),
    POCHI("Pochi", Icons.Default.PhoneAndroid);

    companion object {
        fun fromLabel(label: String): ProfileType =
            entries.firstOrNull { it.label == label } ?: SEND_MONEY
    }
}

data class Profile(
    val id: Int,
    var number: String,
    var type: ProfileType
)

data class ReceivedEntry(
    val number: String,
    val time: String
)

enum class AppTab { HOME, PROFILES }

enum class SendState { IDLE, HOLD_PENDING, TAP_WAVE, PROCESSING, SUCCESS }

enum class ModalType { NONE, ADD_EDIT_PROFILE, SETTINGS, PRIVACY, DELETE_CONFIRM }

fun defaultProfiles(): List<Profile> = listOf(
    Profile(1, "07XXXXXXXX", ProfileType.SEND_MONEY)
)
