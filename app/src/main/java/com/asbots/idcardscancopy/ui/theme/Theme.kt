package com.asbots.idcardscancopy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    secondary = Teal, onSecondary = Color.White,
    secondaryContainer = TealTint, onSecondaryContainer = Ink,
    tertiary = Amber, onTertiary = OnAmber,
    background = Paper, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Mist, onSurfaceVariant = Sub,
    outline = Line, error = ErrorLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Amber, onPrimary = OnAmber,
    secondary = TealLight, onSecondary = Ink,
    secondaryContainer = Color(0xFF0F4A4C), onSecondaryContainer = NightText,
    tertiary = Amber, onTertiary = OnAmber,
    background = NightBg, onBackground = NightText,
    surface = NightSurface, onSurface = NightText,
    surfaceVariant = NightMist, onSurfaceVariant = NightSub,
    outline = NightLine, error = NightError
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun IDCardScanCopyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally off so the brand palette looks the same on every phone.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) NightOk else Ok