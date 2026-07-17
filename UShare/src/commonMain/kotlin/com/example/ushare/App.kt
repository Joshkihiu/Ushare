package com.example.ushare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
    UShareTheme {
        var profiles by remember { mutableStateOf(defaultProfiles()) }
        var nextId by remember { mutableIntStateOf(6) }
        var activeId by remember { mutableIntStateOf(profiles.first().id) }
        var activeTab by remember { mutableStateOf(AppTab.HOME) }
        var receivedEntries by remember { mutableStateOf(listOf<ReceivedEntry>()) }
        var terminalText by remember { mutableStateOf("> Waiting for numbers...") }
        var manualInput by remember { mutableStateOf("") }
        var sendState by remember { mutableStateOf(SendState.IDLE) }
        var soundEnabled by remember { mutableStateOf(true) }
        var volume by remember { mutableFloatStateOf(1.0f) }
        var userName by remember { mutableStateOf("User_992") }
        var profilePhotoBytes by remember { mutableStateOf<ByteArray?>(null) }

        val pickPhoto = rememberImagePickerLauncher { bytes ->
            profilePhotoBytes = bytes
        }

        var showProfileModal by remember { mutableStateOf(false) }
        var showSettingsModal by remember { mutableStateOf(false) }
        var showPrivacyModal by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showUserNameEdit by remember { mutableStateOf(false) }
        var editUserName by remember { mutableStateOf("") }

        var editingId by remember { mutableStateOf<Int?>(null) }
        var modalNumber by remember { mutableStateOf("") }
        var modalType by remember { mutableStateOf(ProfileType.SEND_MONEY) }
        var deleteTargetId by remember { mutableStateOf<Int?>(null) }

        val scope = rememberCoroutineScope()
        var sendJob by remember { mutableStateOf<Job?>(null) }

        val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

        // Sync volume to SignalGenerator whenever it changes
        LaunchedEffect(volume) {
            SignalGenerator.volume = volume
        }

        val onIncomingConfirmed by rememberUpdatedState<(String) -> Unit> { number ->
            // Deduplicate — if already in the log (from raw message), skip
            if (receivedEntries.none { it.number == number }) {
                receivedEntries = receivedEntries + ReceivedEntry(number, currentTimeShort())
                beepIfEnabled(soundEnabled)
            }
        }

        val transceiver = remember(scope) {
            UltrasonicTransceiver(object : TransceiverCallbacks {
                override fun onIncomingConfirmed(number: String) {
                    scope.launch { onIncomingConfirmed(number) }
                }
            })
        }

        DisposableEffect(transceiver) {
            transceiver.startBackgroundReceiver()
            onDispose { transceiver.stopBackgroundReceiver() }
        }

        fun resetTerminalLater(delayMs: Long = 2000) {
            scope.launch {
                delay(delayMs)
                if (sendState == SendState.IDLE) {
                    terminalText = "> Waiting for numbers..."
                }
            }
        }

        val statusPadding = WindowInsets.statusBars.asPaddingValues()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Deep backdrop behind the phone frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            // 3D phone frame container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusPadding.calculateTopPadding() + 4.dp,
                        bottom = 4.dp,
                        start = 8.dp,
                        end = 8.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    shape = RoundedCornerShape(32.dp),
                    color = UShareColors.Base,
                    elevation = 24.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                UShareColors.ShadowLight.copy(alpha = 0.15f),
                                RoundedCornerShape(32.dp)
                            )
                    ) {
                        // Inner content area (with reduced padding since we have the frame)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = 8.dp,
                                    start = 8.dp,
                                    end = 8.dp,
                                    bottom = 90.dp + navBottom
                                )
                        ) {
                            AnimatedTabContent(
                    activeTab = activeTab,
                    homeContent = {
                        HomeScreen(
                            profiles = profiles,
                            activeId = activeId,
                            activeProfile = activeProfile,
                            terminalText = terminalText,
                            manualInput = manualInput,
                            receivedEntries = receivedEntries,
                            onManualInputChange = { manualInput = it },
                            onManualSend = {
                                val value = manualInput.trim()
                                if (value.isNotEmpty()) {
                                    terminalText = "> Sent: $value"
                                    manualInput = ""
                                    scope.launch {
                                        transceiver.manualSend(value)
                                        beepIfEnabled(soundEnabled)
                                    }
                                    resetTerminalLater(2000)
                                }
                            },
                            onClearLog = { receivedEntries = emptyList() },
                            onProfileSelected = { activeId = it },
                            onAddProfile = {
                                editingId = null
                                modalNumber = ""
                                modalType = ProfileType.SEND_MONEY
                                showProfileModal = true
                            }
                        )
                    },
                    profilesContent = {
                        ProfilesScreen(
                            profiles = profiles,
                            userName = userName,
                            photoBytes = profilePhotoBytes,
                            onSettingsClick = { showSettingsModal = true },
                            onUserNameEdit = {
                                editUserName = userName
                                showUserNameEdit = true
                            },
                            onPhotoTap = { pickPhoto() },
                            onEdit = { id ->
                                val profile = profiles.firstOrNull { it.id == id } ?: return@ProfilesScreen
                                editingId = id
                                modalNumber = profile.number
                                modalType = profile.type
                                showProfileModal = true
                            },
                            onDelete = { id ->
                                deleteTargetId = id
                                showDeleteConfirm = true
                            }
                        )
                    }
                )
                            }

                            // BottomNav inside the phone frame
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                BottomNav(
                                    activeTab = activeTab,
                                    sendState = sendState,
                                    bottomInset = 0.dp,
                    onHomeClick = { activeTab = AppTab.HOME },
                    onProfilesClick = { activeTab = AppTab.PROFILES },
                    onSendPressStart = {
                        if (sendState != SendState.IDLE) return@BottomNav
                        // Live lookup — reads the current active profile at the
                        // moment of the press, not a stale captured reference.
                        val num = profiles.firstOrNull { it.id == activeId }?.number ?: return@BottomNav
                        sendState = SendState.HOLD_PENDING
                        sendJob?.cancel()
                        sendJob = scope.launch {
                            delay(500)
                            if (sendState != SendState.HOLD_PENDING) return@launch
                            sendState = SendState.PROCESSING
                            terminalText = "> Sending…"
                            try {
                                val success = transceiver.confirmedSend(num) { phase ->
                                    terminalText = when (phase) {
                                        SendPhase.TRANSMITTING -> "> Sending…"
                                        SendPhase.WAITING_ECHO -> "> Confirming…"
                                        SendPhase.CONFIRMED -> "> Signal received!"
                                        SendPhase.FAILED -> "> Cancelled."
                                    }
                                }
                                if (success && sendState == SendState.PROCESSING) {
                                    // Not added to received log — that's only for
                                    // numbers actually received from another device
                                    beepIfEnabled(soundEnabled)
                                    sendState = SendState.SUCCESS
                                    delay(2000)
                                    sendState = SendState.IDLE
                                    terminalText = "> Waiting for numbers..."
                                } else if (!success && sendState == SendState.PROCESSING) {
                                    sendState = SendState.IDLE
                                    resetTerminalLater(1000)
                                }
                            } catch (_: CancellationException) {
                                if (sendState == SendState.PROCESSING) {
                                    sendState = SendState.IDLE
                                    terminalText = "> Cancelled."
                                    resetTerminalLater(1000)
                                }
                            }
                        }
                    },
                    onSendPressEnd = {
                        when (sendState) {
                            SendState.HOLD_PENDING -> {
                                sendJob?.cancel()
                                val num = profiles.firstOrNull { it.id == activeId }?.number ?: ""
                                sendState = SendState.TAP_WAVE
                                terminalText = "> Sent: $num"
                                scope.launch {
                                    transceiver.quickSend(num)
                                    delay(1000)
                                    sendState = SendState.SUCCESS
                                    delay(800)
                                    sendState = SendState.IDLE
                                    terminalText = "> Waiting for numbers..."
                                }
                            }
                            SendState.PROCESSING -> {
                                sendJob?.cancel()
                                sendState = SendState.IDLE
                                terminalText = "> Cancelled."
                                resetTerminalLater(1000)
                            }
                            else -> Unit
                        }
                    }
                )
                        }
                    }
                }
            }

            ProfileModal(
                visible = showProfileModal,
                isEditing = editingId != null,
                number = modalNumber,
                selectedType = modalType,
                onNumberChange = { modalNumber = it },
                onTypeSelect = { modalType = it },
                onDismiss = {
                    showProfileModal = false
                    editingId = null
                },
                onSave = {
                    val number = modalNumber.trim()
                    if (number.isEmpty()) return@ProfileModal
                    if (editingId != null) {
                        profiles = profiles.map { profile ->
                            if (profile.id == editingId) {
                                profile.copy(number = number, type = modalType)
                            } else profile
                        }
                    } else {
                        // If the only profile is the test placeholder, replace it
                        if (profiles.size == 1 && profiles.first().number == "07XXXXXXXX") {
                            profiles = listOf(Profile(nextId, number, modalType))
                        } else {
                            val newProfile = Profile(nextId, number, modalType)
                            profiles = profiles + newProfile
                        }
                        nextId++
                        activeId = profiles.last().id
                    }
                    showProfileModal = false
                    editingId = null
                }
            )

            SettingsModal(
                visible = showSettingsModal,
                soundEnabled = soundEnabled,
                volume = volume,
                onSoundToggle = { soundEnabled = !soundEnabled },
                onVolumeChange = { volume = it },
                onPrivacyClick = { showPrivacyModal = true },
                onDismiss = { showSettingsModal = false }
            )

            PrivacyModal(
                visible = showPrivacyModal,
                onDismiss = { showPrivacyModal = false }
            )

            DeleteConfirmDialog(
                visible = showDeleteConfirm,
                onConfirm = {
                    val id = deleteTargetId
                    if (id != null) {
                        val wasActive = activeId == id
                        profiles = profiles.filterNot { it.id == id }
                        if (wasActive && profiles.isNotEmpty()) {
                            activeId = profiles.first().id
                        }
                    }
                    showDeleteConfirm = false
                    deleteTargetId = null
                },
                onDismiss = {
                    showDeleteConfirm = false
                    deleteTargetId = null
                }
            )

            // User name edit dialog
            if (showUserNameEdit) {
                androidx.compose.material.AlertDialog(
                    onDismissRequest = { showUserNameEdit = false },
                    backgroundColor = UShareColors.Base,
                    title = {
                        Text("Edit Name", color = UShareColors.TextMain, fontFamily = UShareFonts.ShareTechMono)
                    },
                    text = {
                        androidx.compose.material.TextField(
                            value = editUserName,
                            onValueChange = { editUserName = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = UShareColors.TextMain,
                                fontFamily = UShareFonts.ShareTechMono
                            ),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = UShareColors.Surface,
                                cursorColor = UShareColors.Cyan,
                                focusedIndicatorColor = UShareColors.Cyan,
                                unfocusedIndicatorColor = UShareColors.TextDim.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        androidx.compose.material.TextButton(onClick = {
                            if (editUserName.isNotBlank()) userName = editUserName
                            showUserNameEdit = false
                        }) {
                            Text("Save", color = UShareColors.Cyan)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material.TextButton(onClick = { showUserNameEdit = false }) {
                            Text("Cancel", color = UShareColors.TextDim)
                        }
                    }
                )
            }
        }
    }
}

private fun beepIfEnabled(soundEnabled: Boolean) {
    if (soundEnabled) playBeep()
}

@Composable
private fun HomeScreen(
    profiles: List<Profile>,
    activeId: Int,
    activeProfile: Profile,
    terminalText: String,
    manualInput: String,
    receivedEntries: List<ReceivedEntry>,
    onManualInputChange: (String) -> Unit,
    onManualSend: () -> Unit,
    onClearLog: () -> Unit,
    onProfileSelected: (Int) -> Unit,
    onAddProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {
        HomeCard(
            profiles = profiles,
            activeId = activeId,
            typeLabel = activeProfile.type.label,
            displayNumber = activeProfile.number,
            onAddClick = onAddProfile,
            onProfileSelected = onProfileSelected
        )

        ManualInputField(
            value = manualInput,
            onValueChange = onManualInputChange,
            onSend = onManualSend
        )

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(UShareColors.Base)
                    .border(1.dp, UShareColors.ShadowDark.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RECEIVED",
                        color = UShareColors.TextDim,
                        fontFamily = UShareFonts.Nunito,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear log",
                        tint = UShareColors.TextDim.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onClearLog)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    terminalText,
                    color = Color(0xFF555555),
                    fontFamily = UShareFonts.FiraCode,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                ReceivedLog(
                    entries = receivedEntries,
                    onCopy = { copyToClipboardPlatform(it) }
                )
            }
            // Vertical fade overlay — 100% opacity at top, 0% at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.85f to UShareColors.Base.copy(alpha = 0.5f),
                            1f to UShareColors.Base
                        )
                    )
            )
        }
    }
}

@Composable
private fun ProfilesScreen(
    profiles: List<Profile>,
    userName: String,
    photoBytes: ByteArray?,
    onSettingsClick: () -> Unit,
    onUserNameEdit: () -> Unit,
    onPhotoTap: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        MyProfileCard(
            userName = userName,
            photoBytes = photoBytes,
            onSettingsClick = onSettingsClick,
            onUserNameEdit = onUserNameEdit,
            onPhotoTap = onPhotoTap
        )
        Text(
            "Active Profiles",
            color = UShareColors.TextMain,
            fontFamily = UShareFonts.ShareTechMono,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 5.dp, top = 5.dp)
        )
        profiles.forEach { profile ->
            ProfileListCard(
                profile = profile,
                onEdit = { onEdit(profile.id) },
                onDelete = { onDelete(profile.id) }
            )
        }
    }
}
