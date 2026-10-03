package com.shahaabapps.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand gradients. The accent gradient follows the current [AccentPalette]
 * (Meadow / Blossom / Ocean); avatar gradients add per-name variety.
 */
object Brand {
    val accentGradient: Brush
        @Composable get() = AppTheme.colors.heroBrush

    @Composable
    fun avatarGradient(seed: Int): Brush {
        val palettes = listOf(
            listOf(Color(0xFF14B8A6), Color(0xFF1E8E5A)),
            listOf(Color(0xFF3B6FF6), Color(0xFF7C4DFF)),
            listOf(Color(0xFFFF8FB6), Color(0xFFC13C72)),
            listOf(Color(0xFFFF9500), Color(0xFFFF375F)),
            listOf(Color(0xFFBF5AF2), Color(0xFF0A84FF)),
        )
        val index = (seed % palettes.size + palettes.size) % palettes.size
        return Brush.linearGradient(palettes[index])
    }
}
