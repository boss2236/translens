package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TransLensColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = Accent,
    onSecondary = Ink,
    background = Ink,
    onBackground = OnInk,
    surface = Surface1,
    onSurface = OnInk,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Hairline,
    outlineVariant = Hairline
)

@Composable
fun TransLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TransLensColorScheme,
        typography = Typography,
        content = content
    )
}
