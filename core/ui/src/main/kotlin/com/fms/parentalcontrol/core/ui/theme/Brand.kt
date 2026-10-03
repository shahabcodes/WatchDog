package com.fms.parentalcontrol.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand gradients used on hero areas and primary actions for a modern look.
 */
object Brand {
    val GradientStart = Color(0xFF0A84FF) // iOS blue
    val GradientEnd = Color(0xFF5E5CE6)   // iOS indigo

    val accentGradient: Brush
        @Composable get() = Brush.linearGradient(listOf(GradientStart, GradientEnd))

    @Composable
    fun avatarGradient(seed: Int): Brush {
        val palettes = listOf(
            listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)),
            listOf(Color(0xFF34C759), Color(0xFF30B0C7)),
            listOf(Color(0xFFFF9500), Color(0xFFFF375F)),
            listOf(Color(0xFFBF5AF2), Color(0xFF0A84FF)),
            listOf(Color(0xFFFF2D55), Color(0xFFFF9500)),
        )
        return Brush.linearGradient(palettes[(seed % palettes.size + palettes.size) % palettes.size])
    }
}
