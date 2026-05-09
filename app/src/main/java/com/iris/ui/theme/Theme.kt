package com.iris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IrisColors = darkColorScheme(
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    primary = Color(0xFFFFD600),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    error = Color(0xFFFF6E6E),
    onError = Color(0xFF000000),
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IrisColors,
        typography = IrisTypography,
        content = content,
    )
}
