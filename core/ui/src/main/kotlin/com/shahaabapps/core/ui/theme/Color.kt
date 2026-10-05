package com.shahaabapps.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette inspired by the Browse Safe design system: a refined brand accent,
 * soft neutral surfaces, and semantic status families with tinted backgrounds.
 */
internal object Palette {
    // Neutral surfaces (light)
    val BackgroundLight = Color(0xFFF7F9F8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceVariantLight = Color(0xFFEDF3EF)
    val OutlineLight = Color(0x22000000)
    val LabelLight = Color(0xFF1A1C1E)
    val SecondaryLabelLight = Color(0xFF5A6660)
    val SeparatorLight = Color(0x14000000)

    // Neutral surfaces (dark)
    val BackgroundDark = Color(0xFF111412)
    val SurfaceDark = Color(0xFF1A1E1B)
    val SurfaceVariantDark = Color(0xFF262B27)
    val OutlineDark = Color(0x33FFFFFF)
    val LabelDark = Color(0xFFE3E6E3)
    val SecondaryLabelDark = Color(0xFF9AA69F)
    val SeparatorDark = Color(0x22FFFFFF)

    // Semantic (palette-independent)
    val BlockedLight = Color(0xFFC1362B)
    val BlockedBgLight = Color(0xFFFBEAE8)
    val BlockedDark = Color(0xFFF28B82)
    val BlockedBgDark = Color(0xFF3A1D1A)

    val AllowedLight = Color(0xFF1E8E5A)
    val AllowedBgLight = Color(0xFFE4F1EA)
    val AllowedDark = Color(0xFF7FD1A3)
    val AllowedBgDark = Color(0xFF17301F)

    val WarnLight = Color(0xFFC77700)
    val WarnDark = Color(0xFFFBC02D)

    val SuccessLight = Color(0xFF1E8E5A)
    val SuccessDark = Color(0xFF7FD1A3)
}

/**
 * A selectable accent palette (brand color + hero gradient), independent of
 * light/dark mode — mirrors Browse Safe's Meadow / Blossom / Ocean themes.
 */
enum class AccentPalette(
    val label: String,
    val accentLight: Color,
    val accentDark: Color,
    val gradient: List<Color>,
    // Optional per-palette surface tints (country themes wash the background in
    // their colour). Default to the neutral palette.
    val backgroundLight: Color = Palette.BackgroundLight,
    val backgroundDark: Color = Palette.BackgroundDark,
    val variantLight: Color = Palette.SurfaceVariantLight,
    val variantDark: Color = Palette.SurfaceVariantDark,
    /** Whether this is a nationality/country theme (grouped separately in settings). */
    val isCountry: Boolean = false,
) {
    MEADOW(
        label = "Meadow",
        accentLight = Color(0xFF1E8E5A),
        accentDark = Color(0xFF7FD1A3),
        gradient = listOf(Color(0xFF14B8A6), Color(0xFF12A57E), Color(0xFF1E8E5A)),
    ),
    BLOSSOM(
        label = "Blossom",
        accentLight = Color(0xFFE5588E),
        accentDark = Color(0xFFF48FB1),
        gradient = listOf(Color(0xFFFF8FB6), Color(0xFFE5588E), Color(0xFFC13C72)),
    ),
    OCEAN(
        label = "Ocean",
        accentLight = Color(0xFF2E6BE6),
        accentDark = Color(0xFF6FA0FF),
        gradient = listOf(Color(0xFF3B6FF6), Color(0xFF5B63F7), Color(0xFF7C4DFF)),
    ),

    // ----- Nationality themes (flag-inspired) -----
    QATAR(
        label = "Qatar",
        accentLight = Color(0xFF8A1538),
        accentDark = Color(0xFFE08BA3),
        gradient = listOf(Color(0xFFA81E47), Color(0xFF8A1538), Color(0xFF6E102C)),
        backgroundLight = Color(0xFFFBF2F5),
        backgroundDark = Color(0xFF160A0E),
        variantLight = Color(0xFFF3DFE6),
        variantDark = Color(0xFF2A141A),
        isCountry = true,
    ),
    INDIA(
        label = "India",
        accentLight = Color(0xFFE8710A),
        accentDark = Color(0xFFFFB366),
        gradient = listOf(Color(0xFFFF9933), Color(0xFFF37335), Color(0xFF138808)),
        backgroundLight = Color(0xFFFFF6EC),
        backgroundDark = Color(0xFF15100A),
        variantLight = Color(0xFFFCE7CF),
        variantDark = Color(0xFF2A2014),
        isCountry = true,
    ),
    PAKISTAN(
        label = "Pakistan",
        accentLight = Color(0xFF046A38),
        accentDark = Color(0xFF5FC98E),
        gradient = listOf(Color(0xFF0A8F4E), Color(0xFF046A38), Color(0xFF023D20)),
        backgroundLight = Color(0xFFEEF6F1),
        backgroundDark = Color(0xFF0A140E),
        variantLight = Color(0xFFD6EADE),
        variantDark = Color(0xFF15241B),
        isCountry = true,
    ),
    CANADA(
        label = "Canada",
        accentLight = Color(0xFFD52B1E),
        accentDark = Color(0xFFF0877E),
        gradient = listOf(Color(0xFFE63946), Color(0xFFD52B1E), Color(0xFFA01B12)),
        backgroundLight = Color(0xFFFDF0EF),
        backgroundDark = Color(0xFF170A09),
        variantLight = Color(0xFFF7D9D6),
        variantDark = Color(0xFF2A1513),
        isCountry = true,
    ),
    AUSTRALIA(
        label = "Australia",
        accentLight = Color(0xFF00247D),
        accentDark = Color(0xFF8FB0EA),
        gradient = listOf(Color(0xFF1E4FB0), Color(0xFF00247D), Color(0xFF012169)),
        backgroundLight = Color(0xFFEEF1F8),
        backgroundDark = Color(0xFF0A0D16),
        variantLight = Color(0xFFD6DEF0),
        variantDark = Color(0xFF141A28),
        isCountry = true,
    ),
    IRELAND(
        label = "Ireland",
        accentLight = Color(0xFF169B62),
        accentDark = Color(0xFF6FD3A3),
        gradient = listOf(Color(0xFF169B62), Color(0xFF3FB07A), Color(0xFFFF883E)),
        backgroundLight = Color(0xFFEFF7F2),
        backgroundDark = Color(0xFF0A140E),
        variantLight = Color(0xFFD7EEE0),
        variantDark = Color(0xFF15241B),
        isCountry = true,
    ),
    NEW_ZEALAND(
        label = "New Zealand",
        accentLight = Color(0xFF012169),
        accentDark = Color(0xFF86A6E8),
        gradient = listOf(Color(0xFF1E4FB0), Color(0xFF012169), Color(0xFF00133F)),
        backgroundLight = Color(0xFFEEF1F8),
        backgroundDark = Color(0xFF080B14),
        variantLight = Color(0xFFD4DDF0),
        variantDark = Color(0xFF121826),
        isCountry = true,
    ),
}
