package com.shahaabapps.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scaled to iOS text styles (Large Title, Title, Headline, Body, …).
 *
 * The default [FontFamily] is used so no font files need bundling; swap in an
 * SF-style family here (e.g. "Inter" or "SF Pro") when assets are added.
 */
private val CupertinoFontFamily = FontFamily.Default

val CupertinoTypography = Typography(
    // iOS "Large Title"
    displayLarge = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
    ),
    // "Title 1"
    headlineLarge = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    // "Title 2"
    headlineMedium = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // "Headline"
    titleMedium = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    // "Body"
    bodyLarge = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    // "Subhead"
    bodyMedium = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // "Footnote"
    bodySmall = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // "Caption"
    labelMedium = TextStyle(
        fontFamily = CupertinoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
