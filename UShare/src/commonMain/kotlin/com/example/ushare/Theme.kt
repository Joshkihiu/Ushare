package com.example.ushare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object UShareColors {
    val Base = Color(0xFF1A1A1A)
    val Surface = Color(0xFF1E1E1E)
    val ShadowLight = Color(0xFF282828)
    val ShadowDark = Color(0xFF0C0C0C)
    val Cyan = Color(0xFF0DFCAC)
    val SuccessGreen = Color(0xFF39FF9C)
    val TextMain = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF767676)
    val BubbleBg = Color(0xFF232323)
    val Danger = Color(0xFFFF6B6B)
}

object UShareFonts {
    val ShareTechMono = FontFamily.Monospace
    val Nunito = FontFamily.SansSerif
    val FiraCode = FontFamily.Monospace
}

/**
 * True neumorphic raised effect using layered shadows + a subtle top-left highlight.
 * Uses the system shadow for depth + custom drawn highlights for the neumorphic look.
 */
fun Modifier.neumorphicRaised(
    radius: Dp = 24.dp,
    elevation: Dp = 12.dp
): Modifier = this.drawBehind {
    val corner = CornerRadius(radius.toPx())
    val e = elevation.toPx()
    val halfE = e * 0.5f

    // Dark shadow (bottom-right) — solid, offset
    drawRoundRect(
        color = UShareColors.ShadowDark.copy(alpha = 0.75f),
        topLeft = Offset(halfE, halfE),
        size = Size(size.width, size.height),
        cornerRadius = corner
    )
    // Light highlight (top-left) — subtle
    drawRoundRect(
        color = UShareColors.ShadowLight.copy(alpha = 0.30f),
        topLeft = Offset(-halfE * 0.6f, -halfE * 0.6f),
        size = Size(size.width, size.height),
        cornerRadius = corner
    )
    // Inner rim — thin dark inset edge for definition
    drawRoundRect(
        color = UShareColors.ShadowDark.copy(alpha = 0.18f),
        topLeft = Offset(1f, 1f),
        size = Size(size.width - 2f, size.height - 2f),
        cornerRadius = corner,
        style = Stroke(width = 1f)
    )
}

/**
 * Inset/recessed look — inverted neumorphism.
 */
fun Modifier.neumorphicInset(radius: Dp = 20.dp): Modifier = this.drawBehind {
    val corner = CornerRadius(radius.toPx())
    val e = 8.dp.toPx()
    // Dark shadow inside (top-left inset)
    drawRoundRect(
        color = UShareColors.ShadowDark.copy(alpha = 0.6f),
        topLeft = Offset(-e * 0.3f, -e * 0.3f),
        size = Size(size.width + e * 0.6f, size.height + e * 0.6f),
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )
    // Light edge (bottom-right inset)
    drawRoundRect(
        color = UShareColors.ShadowLight.copy(alpha = 0.15f),
        topLeft = Offset(e * 0.2f, e * 0.2f),
        size = Size(size.width - e * 0.4f, size.height - e * 0.4f),
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )
}

@Composable
fun UShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            background = UShareColors.Base,
            surface = UShareColors.Surface,
            primary = UShareColors.Cyan,
            onBackground = UShareColors.TextMain,
            onSurface = UShareColors.TextMain
        ),
        content = content
    )
}
