package com.banner.cloud.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val XboxGreen = Color(0xFF107C10)
val Accent = Color(0xFF4CC24C)
val Canvas = Color(0xFF0A0C0F)
val Panel = Color(0xFF14171B)
val PanelHigh = Color(0xFF1C2026)
val TextMain = Color(0xFFEDEFF2)
val TextDim = Color(0xFF9AA1A9)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04170A),
    primaryContainer = XboxGreen,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9BD99B),
    background = Canvas,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextDim,
    outline = Color(0xFF353B43),
    error = Color(0xFFFF6B6B),
)

private val base = Typography()
private val type = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun CloudTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
