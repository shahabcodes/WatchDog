package com.shahaabapps.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * App-specific colors beyond Material3's ColorScheme: grouped backgrounds,
 * separators, the current accent + its hero gradient, and semantic status families.
 * Exposed via [LocalCupertinoColors]; read through [AppTheme.colors].
 */
@Immutable
data class CupertinoColors(
    val groupedBackground: Color,
    val card: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val gradient: List<Color>,
    val destructive: Color,
    val success: Color,
    val blocked: Color,
    val blockedBackground: Color,
    val allowed: Color,
    val allowedBackground: Color,
    val warn: Color,
) {
    /** The accent hero gradient as a diagonal brush. */
    val heroBrush: Brush
        get() = Brush.linearGradient(gradient)
}

val LocalCupertinoColors = staticCompositionLocalOf {
    // Light Meadow defaults; always overridden by AppTheme.
    CupertinoColors(
        groupedBackground = Palette.BackgroundLight,
        card = Palette.SurfaceLight,
        secondaryLabel = Palette.SecondaryLabelLight,
        separator = Palette.SeparatorLight,
        accent = AccentPalette.MEADOW.accentLight,
        gradient = AccentPalette.MEADOW.gradient,
        destructive = Palette.BlockedLight,
        success = Palette.SuccessLight,
        blocked = Palette.BlockedLight,
        blockedBackground = Palette.BlockedBgLight,
        allowed = Palette.AllowedLight,
        allowedBackground = Palette.AllowedBgLight,
        warn = Palette.WarnLight,
    )
}
