package com.shahaabapps.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** User-selectable light/dark preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private fun lightColors(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = accent,
    error = Palette.BlockedLight,
    background = Palette.BackgroundLight,
    onBackground = Palette.LabelLight,
    surface = Palette.SurfaceLight,
    onSurface = Palette.LabelLight,
    surfaceVariant = Palette.SurfaceVariantLight,
    outline = Palette.OutlineLight,
)

private fun darkColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0B1F16),
    secondary = accent,
    error = Palette.BlockedDark,
    background = Palette.BackgroundDark,
    onBackground = Palette.LabelDark,
    surface = Palette.SurfaceDark,
    onSurface = Palette.LabelDark,
    surfaceVariant = Palette.SurfaceVariantDark,
    outline = Palette.OutlineDark,
)

private fun cupertinoColors(dark: Boolean, palette: AccentPalette) = CupertinoColors(
    groupedBackground = if (dark) Palette.BackgroundDark else Palette.BackgroundLight,
    card = if (dark) Palette.SurfaceDark else Palette.SurfaceLight,
    secondaryLabel = if (dark) Palette.SecondaryLabelDark else Palette.SecondaryLabelLight,
    separator = if (dark) Palette.SeparatorDark else Palette.SeparatorLight,
    accent = if (dark) palette.accentDark else palette.accentLight,
    gradient = palette.gradient,
    destructive = if (dark) Palette.BlockedDark else Palette.BlockedLight,
    success = if (dark) Palette.SuccessDark else Palette.SuccessLight,
    blocked = if (dark) Palette.BlockedDark else Palette.BlockedLight,
    blockedBackground = if (dark) Palette.BlockedBgDark else Palette.BlockedBgLight,
    allowed = if (dark) Palette.AllowedDark else Palette.AllowedLight,
    allowedBackground = if (dark) Palette.AllowedBgDark else Palette.AllowedBgLight,
    warn = if (dark) Palette.WarnDark else Palette.WarnLight,
)

/**
 * Root theme: Material3 scheme keyed to the chosen [accentPalette], plus the
 * app's [CupertinoColors]. Both apps wrap their content in this.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.MEADOW,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = if (dark) accentPalette.accentDark else accentPalette.accentLight

    CompositionLocalProvider(LocalCupertinoColors provides cupertinoColors(dark, accentPalette)) {
        MaterialTheme(
            colorScheme = if (dark) darkColors(accent) else lightColors(accent),
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
