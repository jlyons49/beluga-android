package com.beluga.e2ee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BelugaCyan,
    onPrimary = BelugaNavyDark,
    primaryContainer = BelugaNavyLight,
    onPrimaryContainer = BelugaCyan,
    secondary = BelugaTeal,
    onSecondary = BelugaNavyDark,
    secondaryContainer = BelugaNavyMedium,
    onSecondaryContainer = BelugaTeal,
    tertiary = BelugaMint,
    onTertiary = BelugaNavyDark,
    background = BelugaNavyDark,
    onBackground = Color.White,
    surface = BelugaNavyMedium,
    onSurface = Color.White,
    surfaceVariant = BelugaNavyLight,
    onSurfaceVariant = Color(0xFFCCD6F6),
    outline = BelugaNavyBorder,
    error = BelugaError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BelugaTealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F1F9),
    onPrimaryContainer = BelugaTealDark,
    secondary = BelugaTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F6FD),
    onSecondaryContainer = Color(0xFF004E64),
    tertiary = Color(0xFF00A896),
    onTertiary = Color.White,
    background = BelugaLightBg,
    onBackground = BelugaNavyDark,
    surface = BelugaLightSurface,
    onSurface = BelugaNavyDark,
    surfaceVariant = BelugaLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = BelugaLightBorder,
    error = BelugaError,
    onError = Color.White
)

@Composable
fun BelugaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BelugaTypography,
        content = content
    )
}
