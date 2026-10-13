package com.shahaabapps.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** User-selectable light/dark preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The currently active accent palette, for UI that adapts to it (e.g. flags). */
val LocalAccentPalette = staticCompositionLocalOf { AccentPalette.NEW_ZEALAND }

// The one fixed brand palette — the sign-in / sign-up (Lighthouse) colours, used
// everywhere so the whole app is navy + gold. The selectable accent is ignored.
private val LhNavy = Color(0xFF012169)        // accent / primary (light)
private val LhNavyOnDark = Color(0xFF86A6E8)  // accent / primary on dark surfaces
private val LhNavyDeep = Color(0xFF00133F)
private val LhGradient = listOf(Color(0xFF1E4FB0), Color(0xFF012169), Color(0xFF00133F))
private val LhBackgroundLight = Color(0xFFEEF1F8)
private val LhBackgroundDark = Color(0xFF080B14)
private val LhVariantLight = Color(0xFFD4DDF0)
private val LhVariantDark = Color(0xFF121826)
private val LhSecondaryLight = Color(0xFF4A5878)

private fun lightColors() = lightColorScheme(
    primary = LhNavy,
    onPrimary = Color.White,
    secondary = LhNavy,
    error = Palette.BlockedLight,
    background = LhBackgroundLight,
    onBackground = Palette.LabelLight,
    surface = Palette.SurfaceLight,
    onSurface = Palette.LabelLight,
    surfaceVariant = LhVariantLight,
    outline = Palette.OutlineLight,
)

private fun darkColors() = darkColorScheme(
    primary = LhNavyOnDark,
    onPrimary = LhNavyDeep,
    secondary = LhNavyOnDark,
    error = Palette.BlockedDark,
    background = LhBackgroundDark,
    onBackground = Palette.LabelDark,
    surface = Palette.SurfaceDark,
    onSurface = Palette.LabelDark,
    surfaceVariant = LhVariantDark,
    outline = Palette.OutlineDark,
)

private fun cupertinoColors(dark: Boolean) = CupertinoColors(
    groupedBackground = if (dark) LhBackgroundDark else LhBackgroundLight,
    card = if (dark) Palette.SurfaceDark else Palette.SurfaceLight,
    secondaryLabel = if (dark) Palette.SecondaryLabelDark else LhSecondaryLight,
    separator = if (dark) Palette.SeparatorDark else Palette.SeparatorLight,
    accent = if (dark) LhNavyOnDark else LhNavy,
    gold = Palette.Gold,
    onGold = Palette.OnGold,
    gradient = LhGradient,
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
    accentPalette: AccentPalette = AccentPalette.GUARDIAN,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalCupertinoColors provides cupertinoColors(dark),
        LocalAccentPalette provides accentPalette,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkColors() else lightColors(),
            typography = CupertinoTypography,
        ) {
            // Make Figtree the default for plain Text() that sets only size/weight
            // (they otherwise fall back to the system font).
            CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides
                    androidx.compose.material3.LocalTextStyle.current.merge(TextStyle(fontFamily = AppFonts.body)),
                content = content,
            )
        }
    }
}

/** Convenience accessor for the current [CupertinoColors]. */
object AppTheme {
    val colors: CupertinoColors
        @Composable get() = LocalCupertinoColors.current

    val palette: AccentPalette
        @Composable get() = LocalAccentPalette.current
}
