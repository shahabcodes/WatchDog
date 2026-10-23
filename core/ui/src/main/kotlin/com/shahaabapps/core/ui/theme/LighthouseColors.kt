package com.shahaabapps.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The fixed colours of the full-navy "Lighthouse" screens (Welcome, Sign in, Pair,
 * Verify, Connected, the App guide). These pages are always navy, in light and in
 * dark mode, so they are constants rather than theme tokens.
 */
object LighthouseColors {
    /** Page background of the navy screens, and the ink on gold. */
    val NavyDeep = Color(0xFF00133F)
    /** Brand navy — the light-mode accent. */
    val Navy = Color(0xFF012169)
    /** Middle stop of the hero gradient. */
    val NavyMid = Color(0xFF1E4FB0)
    /** Light navy — the dark-mode accent, and accent ink drawn on navy. */
    val NavyLight = Color(0xFF86A6E8)
    /** 1.5dp outline of secondary buttons and back circles on navy. */
    val Outline = Color(0xFF3A5AA8)
    /** Secondary text on navy (header subtitles). */
    val Subtitle = Color(0xFFC7D3F0)
    /** Brand gold — CTA fill and eyebrow labels. */
    val Gold = Palette.Gold
    /** Night-tone surface of the Bedtime banner. */
    val Night = Color(0xFF1C2140)
    /** Lilac ink on [Night]. */
    val Lilac = Color(0xFFC9CFF5)
}
