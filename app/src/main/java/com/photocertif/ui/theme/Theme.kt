package com.photocertif.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF00BCD4),   // Cyan — CTA principal
    secondary        = Color(0xFF00E676),   // Vert émeraude — succès / indicateurs
    tertiary         = Color(0xFF7C4DFF),   // Violet — accents
    background       = Color(0xFF0A0A14),   // Noir profond
    surface          = Color(0xFF13131F),   // Surface légèrement éclairée
    surfaceVariant   = Color(0xFF1E1E30),
    onPrimary        = Color(0xFF001F26),
    onSecondary      = Color(0xFF00210E),
    onBackground     = Color(0xFFE8E8F0),
    onSurface        = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFF9898B0),
    error            = Color(0xFFFF5252),
    outline          = Color(0xFF2E2E45),
)

@Composable
fun PhotoCertifTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
