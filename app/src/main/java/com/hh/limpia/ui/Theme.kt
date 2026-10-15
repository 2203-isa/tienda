package com.hh.limpia.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF3949AB),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDDE6E3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4DD0B5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFB2F2E3),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3E2000),
    secondaryContainer = Color(0xFF5C3100),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF9FA8DA),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A16),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF26302D),
)

@Composable
fun HHTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
