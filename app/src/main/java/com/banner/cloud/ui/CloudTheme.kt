package com.banner.cloud.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XboxGreen = Color(0xFF107C10)

private val colors = darkColorScheme(
    primary = Color(0xFF3FBF3F),
    onPrimary = Color.Black,
    primaryContainer = XboxGreen,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9BD99B),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF15181C),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF22262B),
    onSurfaceVariant = Color(0xFFB5BAC0),
    outline = Color(0xFF3A4047),
)

@Composable
fun CloudTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
