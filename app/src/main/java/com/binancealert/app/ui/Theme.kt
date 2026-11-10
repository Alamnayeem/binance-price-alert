package com.binancealert.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Yellow = Color(0xFFF0B90B)
val Green = Color(0xFF0ECB81)
val Red = Color(0xFFF6465D)
val Muted = Color(0xFF848E9C)
val CardBg = Color(0xFF1E2329)
val Surface0 = Color(0xFF181A20)

private val Scheme = darkColorScheme(
    primary = Yellow,
    onPrimary = Color(0xFF181A20),
    secondary = Green,
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFEAECEF),
    surface = Surface0,
    onSurface = Color(0xFFEAECEF),
    surfaceVariant = CardBg,
    onSurfaceVariant = Muted,
    error = Red,
    outline = Color(0xFF2B3139)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
