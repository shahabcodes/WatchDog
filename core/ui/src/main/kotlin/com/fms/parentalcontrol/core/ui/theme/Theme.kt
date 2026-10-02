package com.fms.parentalcontrol.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** User-selectable theme preference, persisted by each app. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColorScheme = lightColorScheme(
    primary = CupertinoPalette.BlueLight,
    onPrimary = CupertinoPalette.LabelDark,
    secondary = CupertinoPalette.GreenLight,
    error = CupertinoPalette.RedLight,
    background = CupertinoPalette.GroupedBackgroundLight,
    onBackground = CupertinoPalette.LabelLight,
    surface = CupertinoPalette.CardLight,
    onSurface = CupertinoPalette.LabelLight,
    surfaceVariant = CupertinoPalette.SecondaryBackgroundLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = CupertinoPalette.BlueDark,
    onPrimary = CupertinoPalette.LabelDark,
    secondary = CupertinoPalette.GreenDark,
    error = CupertinoPalette.RedDark,
    background = CupertinoPalette.GroupedBackgroundDark,
    onBackground = CupertinoPalette.LabelDark,
    surface = CupertinoPalette.CardDark,
    onSurface = CupertinoPalette.LabelDark,
    surfaceVariant = CupertinoPalette.SecondaryBackgroundDark,
)

private val LightCupertinoColors = CupertinoColors(
    groupedBackground = CupertinoPalette.GroupedBackgroundLight,
    card = CupertinoPalette.CardLight,
    secondaryLabel = CupertinoPalette.SecondaryLabelLight,
    separator = CupertinoPalette.SeparatorLight,
    accent = CupertinoPalette.BlueLight,
    destructive = CupertinoPalette.RedLight,
    success = CupertinoPalette.GreenLight,
)

private val DarkCupertinoColors = CupertinoColors(
    groupedBackground = CupertinoPalette.GroupedBackgroundDark,
    card = CupertinoPalette.CardDark,
    secondaryLabel = CupertinoPalette.SecondaryLabelDark,
    separator = CupertinoPalette.SeparatorDark,
    accent = CupertinoPalette.BlueDark,
    destructive = CupertinoPalette.RedDark,
    success = CupertinoPalette.GreenDark,
)

/**
 * Root theme shared by both apps. Provides a Material3 scheme mapped to the
 * Cupertino palette plus the iOS-specific [CupertinoColors] via composition local.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val cupertinoColors = if (darkTheme) DarkCupertinoColors else LightCupertinoColors

    CompositionLocalProvider(LocalCupertinoColors provides cupertinoColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CupertinoTypography,
            content = content,
        )
    }
}

/** Convenience accessor for the current [CupertinoColors]. */
object AppTheme {
    val colors: CupertinoColors
        @Composable get() = LocalCupertinoColors.current
}
