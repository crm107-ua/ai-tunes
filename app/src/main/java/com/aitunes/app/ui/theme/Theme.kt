package com.aitunes.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal400,
    secondary = Teal200,
    tertiary = TealAccent,
    background = PureBlack,
    surface = PureBlack,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onTertiary = PureBlack,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
)

@Composable
fun AiTunesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
