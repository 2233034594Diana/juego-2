package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OceanDeltaDark,
    secondary = SlateNavy,
    tertiary = EmeraldGreenDark,
    background = WarmSandDark,
    surface = WarmSandDark,
    onPrimary = CharcoalDarkLight,
    onSecondary = CharcoalDarkLight,
    onTertiary = CharcoalDarkLight,
    onBackground = CharcoalDarkLight,
    onSurface = CharcoalDarkLight
)

private val LightColorScheme = lightColorScheme(
    primary = SlateNavy,
    secondary = OceanDelta,
    tertiary = EmeraldGreen,
    background = WarmSand,
    surface = CoolGrey,
    onPrimary = WarmSand,
    onSecondary = WarmSand,
    onTertiary = WarmSand,
    onBackground = CharcoalDark,
    onSurface = CharcoalDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
