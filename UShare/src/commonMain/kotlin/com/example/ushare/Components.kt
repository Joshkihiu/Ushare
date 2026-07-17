package com.example.ushare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    glow: Boolean = false
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = UShareColors.Cyan.copy(alpha = 0.35f),
                modifier = Modifier.size(size + 6.dp)
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun HomeCard(
    profiles: List<Profile>,
    activeId: Int,
    typeLabel: String,
    displayNumber: String,
    onAddClick: () -> Unit,
    onProfileSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(radius = 30.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(UShareColors.Base)
            .padding(vertical = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = typeLabel.uppercase(),
                color = UShareColors.Cyan,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            AppIcon(
                Icons.Default.Add,
                contentDescription = "Add profile",
                tint = UShareColors.TextDim,
                size = 20.dp,
                modifier = Modifier.clickable(onClick = onAddClick)
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayNumber,
                color = Color(0xFF111111),
                fontFamily = UShareFonts.ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.offset(x = 2.dp, y = 2.dp)
            )
            Text(
                text = displayNumber,
                color = Color(0xFF000000),
                fontFamily = UShareFonts.ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.offset(x = 4.dp, y = 4.dp),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                        blurRadius = 8f
                    )
                )
            )
            Text(
                text = displayNumber,
                color = Color(0xFFF0F0F0),
                fontFamily = UShareFonts.ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = 4.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.15f),
                        offset = androidx.compose.ui.geometry.Offset(-1f, -1f),
                        blurRadius = 2f
                    )
                )
            )
        }

        Spacer(Modifier.height(15.dp))

        ProfileStrip(
            profiles = profiles,
            activeId = activeId,
            onProfileSelected = onProfileSelected
        )
    }
}

@Composable
fun ProfileStrip(
    profiles: List<Profile>,
    activeId: Int,
    onProfileSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val centeredId by remember(profiles) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf activeId
            val viewportCenter =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }
                ?.let { profiles.getOrNull(it.index)?.id }
                ?: activeId
        }
    }

    LaunchedEffect(centeredId) {
        if (centeredId != activeId) onProfileSelected(centeredId)
    }

    LaunchedEffect(activeId, profiles.size) {
        val index = profiles.indexOfFirst { it.id == activeId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 140.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isActive = profile.id == activeId
            val size by animateDpAsState(if (isActive) 70.dp else 50.dp, label = "iconSize")
            val bg by androidx.compose.animation.animateColorAsState(
                if (isActive) UShareColors.Cyan else UShareColors.Base,
                label = "iconBg"
            )
            val iconTint = if (isActive) Color(0xFF111111) else Color(0xFF888888)

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable {
                        val index = profiles.indexOfFirst { it.id == profile.id }
                        if (index >= 0) {
                            onProfileSelected(profile.id)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    profile.type.icon,
                    contentDescription = profile.type.label,
                    tint = iconTint,
                    modifier = Modifier.size(if (isActive) 24.dp else 18.dp)
                )
            }
        }
    }
}

@Composable
fun ManualInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val hasText = value.trim().isNotEmpty()
    val borderColor = if (hasText) UShareColors.Cyan else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(radius = 22.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(UShareColors.Base)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(UShareColors.Base)
                .then(
                if (hasText) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = UShareColors.Cyan.copy(alpha = 0.15f),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            size = size
                        )
                    }.border(2.dp, UShareColors.Cyan, RoundedCornerShape(20.dp))
                } else {
                    Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(20.dp))
                }
            )
    ) {
            TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    "Enter temporary number...",
                    color = UShareColors.TextDim,
                    fontFamily = UShareFonts.ShareTechMono
                )
            },
            textStyle = TextStyle(
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 16.sp
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                cursorColor = UShareColors.Cyan,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(end = if (hasText) 40.dp else 0.dp)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = hasText,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                Icons.Outlined.Send,
                contentDescription = "Send",
                tint = UShareColors.Cyan,
                modifier = Modifier
                    .padding(end = 20.dp)
                    .size(20.dp)
                    .clickable(onClick = onSend)
            )
        }
    }
    }
}

@Composable
fun ReceivedLog(
    entries: List<ReceivedEntry>,
    onCopy: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { "${it.number}-${it.time}" }) { entry ->
            LogBubble(entry, onCopy)
        }
    }
}

@Composable
fun LogBubble(entry: ReceivedEntry, onCopy: (String) -> Unit) {
    var copied by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (visible) 1f else 0.96f, label = "bubbleScale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "bubbleAlpha")

    LaunchedEffect(Unit) {
        visible = true
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 4.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationX = if (visible) 0f else -10f
            }
            .clip(bubbleShape)
            .background(UShareColors.BubbleBg)
            .padding(vertical = 12.dp, horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(UShareColors.Cyan)
        )
        Icon(
            Icons.Outlined.Email,
            contentDescription = null,
            tint = UShareColors.Cyan.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = entry.number,
            color = Color(0xFFF0F0F0),
            fontFamily = UShareFonts.ShareTechMono,
            fontSize = 15.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = entry.time,
            color = UShareColors.TextDim.copy(alpha = 0.6f),
            fontFamily = UShareFonts.Nunito,
            fontSize = 10.sp
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (copied) UShareColors.SuccessGreen else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(8.dp)
                )
                .background(
                    if (copied) UShareColors.SuccessGreen.copy(alpha = 0.06f)
                    else Color.Transparent
                )
                .clickable {
                    onCopy(entry.number)
                    copied = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = if (copied) UShareColors.SuccessGreen else UShareColors.TextDim.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
}

@Composable
fun BottomNav(
    activeTab: AppTab,
    sendState: SendState,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onHomeClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onSendPressStart: () -> Unit,
    onSendPressEnd: () -> Unit
) {
    val showWaves = sendState == SendState.TAP_WAVE || sendState == SendState.PROCESSING
    val sideOffset by animateDpAsState(
        if (showWaves) 35.dp else 0.dp,
        label = "sideOffset"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp + bottomInset),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            Icons.Outlined.Home,
            contentDescription = "Home",
            tint = if (activeTab == AppTab.HOME) UShareColors.Cyan else UShareColors.TextDim,
            glow = activeTab == AppTab.HOME,
            modifier = Modifier
                .offset(x = -sideOffset)
                .pointerInput(onHomeClick) {
                    detectTapGestures(onTap = { onHomeClick() })
                }
        )

        Box(contentAlignment = Alignment.Center) {
            if (showWaves) {
                WaveRipple(continuous = sendState == SendState.PROCESSING, delayMs = 0)
                WaveRipple(continuous = sendState == SendState.PROCESSING, delayMs = if (sendState == SendState.PROCESSING) 800 else 150)
            }

            val sendColor = when (sendState) {
                SendState.SUCCESS -> UShareColors.SuccessGreen
                SendState.PROCESSING -> UShareColors.TextMain
                else -> UShareColors.Cyan
            }

            val glowAlpha = if (sendState == SendState.SUCCESS) 0.65f else 0f
            val processingGlow = sendState == SendState.PROCESSING

            Box(
                modifier = Modifier
                    .size(width = 85.dp, height = 60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(UShareColors.Base)
                    .neumorphicRaised(radius = 20.dp)
                    .then(
                        if (processingGlow) {
                            Modifier.drawBehind {
                                drawRoundRect(
                                    color = UShareColors.Cyan.copy(alpha = 0.12f),
                                    cornerRadius = CornerRadius(20.dp.toPx()),
                                    size = size
                                )
                            }
                        } else Modifier
                    )
                    .border(
                        width = if (glowAlpha > 0f) 2.dp else 0.dp,
                        color = UShareColors.SuccessGreen.copy(alpha = glowAlpha),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onSendPressStart()
                                tryAwaitRelease()
                                onSendPressEnd()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = sendColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        AppIcon(
            Icons.Outlined.Person,
            contentDescription = "Profiles",
            tint = if (activeTab == AppTab.PROFILES) UShareColors.Cyan else UShareColors.TextDim,
            glow = activeTab == AppTab.PROFILES,
            modifier = Modifier
                .offset(x = sideOffset)
                .pointerInput(onProfilesClick) {
                    detectTapGestures(onTap = { onProfilesClick() })
                }
        )
    }
}

@Composable
private fun WaveRipple(continuous: Boolean, delayMs: Int) {
    if (continuous) {
        // Continuous looping wave for long-press processing state
        val infiniteTransition = rememberInfiniteTransition(label = "waveCont")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, delayMillis = delayMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waveProgress"
        )
        val scale = 1f + progress * 2.5f
        val alpha = 1f - progress
        WaveRing(scale, alpha)
    } else {
        // Single-shot wave for quick tap — plays once, then disappears
        var playOnce by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0f) }
        LaunchedEffect(Unit) {
            delay(delayMs.toLong())
            playOnce = true
            val anim = androidx.compose.animation.core.Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = LinearEasing)
            )
            progress = 1f
        }
        if (playOnce && progress < 1f) {
            val p = progress
            WaveRing(1f + p * 2.5f, 1f - p)
        }
    }
}

@Composable
private fun WaveRing(scale: Float, alpha: Float) {
    Box(
        modifier = Modifier
            .size(width = 85.dp, height = 60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f + (scale - 1f) * 0.2f
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(20.dp))
    )
}

@Composable
fun MyProfileCard(
    userName: String,
    photoBytes: ByteArray? = null,
    onSettingsClick: () -> Unit,
    onUserNameEdit: () -> Unit,
    onPhotoTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(UShareColors.Surface)
            .border(1.dp, UShareColors.ShadowLight.copy(alpha = 0.15f), RoundedCornerShape(30.dp))
            .padding(25.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MY PROFILE",
                color = UShareColors.Cyan,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = UShareColors.TextDim,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onSettingsClick)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(UShareColors.Base)
                    .border(1.dp, UShareColors.ShadowDark.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onPhotoTap),
                contentAlignment = Alignment.Center
            ) {
                if (photoBytes != null) {
                    // Show picked photo
                    val bitmap = remember(photoBytes) { decodeByteArrayToImageBitmap(photoBytes) }
                    if (bitmap != null) {
                        Icon(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(bitmap),
                            contentDescription = "Profile photo",
                            tint = Color.Unspecified,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.SupervisedUserCircle,
                            contentDescription = null,
                            tint = UShareColors.Cyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.SupervisedUserCircle,
                        contentDescription = null,
                        tint = UShareColors.Cyan,
                        modifier = Modifier.size(36.dp)
                    )
                }
                // Camera overlay icon (always visible)
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (photoBytes != null) "Change photo" else "Add photo",
                    tint = UShareColors.Cyan.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                )
            }
            Spacer(Modifier.height(15.dp))
            Text(
                userName,
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.clickable(onClick = onUserNameEdit)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "SECURE CONNECTION",
                color = UShareColors.TextDim,
                fontFamily = UShareFonts.Nunito,
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun ProfileListCard(
    profile: Profile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(UShareColors.Surface)
            .border(1.dp, UShareColors.ShadowLight.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(
            profile.type.icon,
            contentDescription = null,
            tint = UShareColors.Cyan,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.number,
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                profile.type.label,
                color = UShareColors.TextDim,
                fontFamily = UShareFonts.Nunito,
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Edit",
            tint = UShareColors.TextDim.copy(alpha = 0.9f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onEdit)
        )
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = UShareColors.TextDim.copy(alpha = 0.9f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
fun ModalOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + scaleIn(
            initialScale = 0.95f,
            animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f))
        ),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume clicks */ }
            ) {
                content()
            }
        }
    }
}

@Composable
fun ProfileModal(
    visible: Boolean,
    isEditing: Boolean,
    number: String,
    selectedType: ProfileType,
    onNumberChange: (String) -> Unit,
    onTypeSelect: (ProfileType) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    ModalOverlay(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(UShareColors.Surface)
                .border(1.dp, UShareColors.ShadowLight.copy(alpha = 0.15f), RoundedCornerShape(25.dp))
                .padding(25.dp)
        ) {
            Text(
                if (isEditing) "Edit Profile" else "Add New Profile",
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                value = number,
                onValueChange = onNumberChange,
                placeholder = { Text("Number", color = UShareColors.TextDim) },
                textStyle = TextStyle(
                    color = UShareColors.TextMain,
                    fontFamily = UShareFonts.ShareTechMono
                ),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = UShareColors.Base,
                    cursorColor = UShareColors.Cyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(UShareColors.Base)
                    .border(1.dp, UShareColors.ShadowDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Type",
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeChip(ProfileType.SEND_MONEY, selectedType, onTypeSelect)
                    TypeChip(ProfileType.TILL, selectedType, onTypeSelect)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeChip(ProfileType.PAYBILL, selectedType, onTypeSelect)
                    TypeChip(ProfileType.POCHI, selectedType, onTypeSelect)
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = UShareColors.TextDim)
                }
                Spacer(Modifier.width(15.dp))
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(backgroundColor = UShareColors.Cyan),
                    shape = RoundedCornerShape(15.dp),
                    elevation = ButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Text("Save", color = Color(0xFF111111), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    type: ProfileType,
    selectedType: ProfileType,
    onTypeSelect: (ProfileType) -> Unit
) {
    val selected = type == selectedType
    Text(
        text = type.label,
        color = if (selected) Color(0xFF111111) else UShareColors.TextDim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) Modifier.background(UShareColors.Cyan)
                else Modifier.background(UShareColors.Base).border(1.dp, UShareColors.ShadowDark.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            )
            .clickable { onTypeSelect(type) }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsModal(
    visible: Boolean,
    soundEnabled: Boolean,
    volume: Float,
    onSoundToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPrivacyClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalOverlay(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(UShareColors.Surface)
                .border(1.dp, UShareColors.ShadowLight.copy(alpha = 0.15f), RoundedCornerShape(25.dp))
                .padding(25.dp)
        ) {
            Text(
                "Settings",
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(20.dp))

            // Sending Sound toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sending Sound", color = UShareColors.TextMain, fontFamily = UShareFonts.Nunito, fontSize = 16.sp)
                SoundToggle(enabled = soundEnabled, onToggle = onSoundToggle)
            }

            Spacer(Modifier.height(16.dp))

            // Ultrasonic Volume slider
            Text(
                "US Volume",
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.Nunito,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Low",
                    color = UShareColors.TextDim,
                    fontFamily = UShareFonts.Nunito,
                    fontSize = 11.sp
                )
                androidx.compose.material.Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material.SliderDefaults.colors(
                        thumbColor = UShareColors.Cyan,
                        activeTrackColor = UShareColors.Cyan,
                        inactiveTrackColor = UShareColors.ShadowDark
                    )
                )
                Text(
                    "High",
                    color = UShareColors.TextDim,
                    fontFamily = UShareFonts.Nunito,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Privacy Policy",
                color = UShareColors.Cyan,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onPrivacyClick)
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = UShareColors.TextDim)
                }
            }
        }
    }
}

@Composable
fun PrivacyModal(visible: Boolean, onDismiss: () -> Unit) {
    ModalOverlay(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(UShareColors.Surface)
                .border(1.dp, UShareColors.ShadowLight.copy(alpha = 0.15f), RoundedCornerShape(25.dp))
                .padding(25.dp)
        ) {
            Text(
                "Privacy Policy",
                color = UShareColors.TextMain,
                fontFamily = UShareFonts.ShareTechMono,
                fontSize = 18.sp
            )
            Column(
                modifier = Modifier
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    "We value your privacy. This app does not collect, store, or share any personal data. " +
                        "All information entered remains on your device. We do not use cookies or tracking. " +
                        "For any concerns, contact us at support@example.com.",
                    color = Color(0xFFAAAAAA),
                    fontFamily = UShareFonts.Nunito,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Last updated: July 2026",
                    color = Color(0xFFAAAAAA),
                    fontFamily = UShareFonts.Nunito,
                    fontSize = 13.sp
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = UShareColors.TextDim)
                }
            }
        }
    }
}

/**
 * 3D decorative glow line — fades from cyan (top) to transparent (bottom),
 * giving a raised-edge light reflection effect.
 */
@Composable
fun GlowLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        UShareColors.Cyan.copy(alpha = 0.25f),
                        UShareColors.Cyan.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun SoundToggle(enabled: Boolean, onToggle: () -> Unit) {
    val offset by animateDpAsState(if (enabled) 20.dp else 0.dp, label = "toggleKnob")
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) UShareColors.Cyan else UShareColors.ShadowDark)
            .clickable(onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun DeleteConfirmDialog(visible: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            backgroundColor = UShareColors.Base,
            title = {
                Text("Delete Profile", color = UShareColors.TextMain, fontFamily = UShareFonts.ShareTechMono)
            },
            text = {
                Text("Delete this profile?", color = UShareColors.TextDim, fontFamily = UShareFonts.Nunito)
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = UShareColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = UShareColors.TextDim)
                }
            }
        )
    }
}

@Composable
fun AnimatedTabContent(
    activeTab: AppTab,
    homeContent: @Composable () -> Unit,
    profilesContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = activeTab == AppTab.HOME,
            enter = slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f))
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f))
            ) + fadeOut()
        ) {
            homeContent()
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = activeTab == AppTab.PROFILES,
            enter = slideInHorizontally(
                initialOffsetX = { it / 3 },
                animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f))
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { it / 3 },
                animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f))
            ) + fadeOut()
        ) {
            profilesContent()
        }
    }
}
