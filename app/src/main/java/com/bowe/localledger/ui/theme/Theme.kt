package com.bowe.localledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LedgerSlate,
    onPrimary = LedgerPaper,
    primaryContainer = LedgerFog,
    onPrimaryContainer = LedgerInk,
    secondary = LedgerClay,
    onSecondary = LedgerInk,
    secondaryContainer = LedgerSand,
    onSecondaryContainer = LedgerInk,
    tertiary = LedgerMoss,
    onTertiary = LedgerPaper,
    tertiaryContainer = LedgerSage,
    onTertiaryContainer = LedgerInk,
    background = LedgerLinen,
    onBackground = LedgerInk,
    surface = LedgerPaper,
    onSurface = LedgerInk,
    surfaceVariant = LedgerFog,
    onSurfaceVariant = LedgerMoss,
    outline = LedgerStone,
    outlineVariant = LedgerFog,
)

private val DarkColors = darkColorScheme(
    primary = LedgerSage,
    onPrimary = LedgerNight,
    primaryContainer = LedgerSlate,
    onPrimaryContainer = LedgerPaper,
    secondary = LedgerSand,
    onSecondary = LedgerNight,
    secondaryContainer = LedgerClay,
    onSecondaryContainer = LedgerPaper,
    tertiary = LedgerFog,
    onTertiary = LedgerNight,
    tertiaryContainer = LedgerNightMuted,
    onTertiaryContainer = LedgerPaper,
    background = LedgerNight,
    onBackground = LedgerPaper,
    surface = LedgerNightSoft,
    onSurface = LedgerPaper,
    surfaceVariant = LedgerNightMuted,
    onSurfaceVariant = LedgerSage,
    outline = LedgerMoss,
    outlineVariant = LedgerNightMuted,
)

@Composable
fun LocalLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
