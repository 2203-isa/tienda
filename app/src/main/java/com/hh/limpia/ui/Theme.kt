package com.hh.limpia.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores tomados del logo de HH SmartClean AI (azul eléctrico + naranja de la carpeta).
private val Light = lightColorScheme(
    primary = Color(0xFF0B5FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFFE67700),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2BF),
    onSecondaryContainer = Color(0xFF2E1600),
    tertiary = Color(0xFF00A6E0),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF5F8FE),
    surface = Color(0xFFF5F8FE),
    surfaceVariant = Color(0xFFDDE5F3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CC4FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF0A438F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFFFB866),
    onSecondary = Color(0xFF452400),
    secondaryContainer = Color(0xFF633500),
    onSecondaryContainer = Color(0xFFFFE2BF),
    tertiary = Color(0xFF5CD3FF),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A16),
    background = Color(0xFF070D1C),
    surface = Color(0xFF070D1C),
    surfaceVariant = Color(0xFF1C2638),
)

@Composable
fun HHTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
