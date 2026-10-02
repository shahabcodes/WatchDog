package com.fms.parentalcontrol.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS (Cupertino) system palette. Values mirror Apple's Human Interface
 * guidelines for system colors in light and dark appearance.
 */
internal object CupertinoPalette {
    // System blue (primary accent)
    val BlueLight = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)

    val GreenLight = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)

    val RedLight = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)

    val OrangeLight = Color(0xFFFF9500)
    val OrangeDark = Color(0xFFFF9F0A)

    // Backgrounds — grouped (settings-style) surfaces
    val SystemBackgroundLight = Color(0xFFFFFFFF)
    val SystemBackgroundDark = Color(0xFF000000)

    val SecondaryBackgroundLight = Color(0xFFF2F2F7)
    val SecondaryBackgroundDark = Color(0xFF1C1C1E)

    val GroupedBackgroundLight = Color(0xFFF2F2F7)
    val GroupedBackgroundDark = Color(0xFF000000)

    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF1C1C1E)

    // Labels
    val LabelLight = Color(0xFF000000)
    val LabelDark = Color(0xFFFFFFFF)

    val SecondaryLabelLight = Color(0x993C3C43) // 60% opacity
    val SecondaryLabelDark = Color(0x99EBEBF5)

    val SeparatorLight = Color(0x5C3C3C43)
    val SeparatorDark = Color(0x5CEBEBF5)
}
