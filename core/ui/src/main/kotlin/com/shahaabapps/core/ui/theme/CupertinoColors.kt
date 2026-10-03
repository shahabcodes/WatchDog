package com.shahaabapps.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS-specific colors that Material3's [androidx.compose.material3.ColorScheme]
 * does not model directly (grouped backgrounds, separators, secondary labels).
 * Exposed via [LocalCupertinoColors].
 */
@Immutable
data class CupertinoColors(
    val groupedBackground: Color,
    val card: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val destructive: Color,
    val success: Color,
)

val LocalCupertinoColors = staticCompositionLocalOf {
    // Sensible light defaults; always overridden by AppTheme.
    CupertinoColors(
        groupedBackground = CupertinoPalette.GroupedBackgroundLight,
        card = CupertinoPalette.CardLight,
        secondaryLabel = CupertinoPalette.SecondaryLabelLight,
        separator = CupertinoPalette.SeparatorLight,
        accent = CupertinoPalette.BlueLight,
        destructive = CupertinoPalette.RedLight,
        success = CupertinoPalette.GreenLight,
    )
}
