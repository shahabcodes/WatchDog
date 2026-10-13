@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.shahaabapps.core.ui.theme


import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.R

private fun fig(weight: Int, fw: FontWeight) =
    Font(R.font.figtree, fw, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun bric(weight: Int, fw: FontWeight) =
    Font(R.font.bricolage_grotesque, fw, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun sora(weight: Int, fw: FontWeight) =
    Font(R.font.sora, fw, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Sora — the app's typeface (Lighthouse), used everywhere for one consistent feel. */
val SoraFamily = FontFamily(
    sora(400, FontWeight.Normal),
    sora(500, FontWeight.Medium),
    sora(600, FontWeight.SemiBold),
    sora(700, FontWeight.Bold),
    sora(800, FontWeight.ExtraBold),
)

/** Figtree — the body / UI typeface (Direction 1). */
val FigtreeFamily = FontFamily(
    fig(400, FontWeight.Normal),
    fig(500, FontWeight.Medium),
    fig(600, FontWeight.SemiBold),
    fig(700, FontWeight.Bold),
)

/** Bricolage Grotesque — the display typeface for big titles and numbers. */
val BricolageFamily = FontFamily(
    bric(600, FontWeight.SemiBold),
    bric(700, FontWeight.Bold),
    bric(800, FontWeight.ExtraBold),
)

/** App font families: [body] everywhere, [display] for big titles and key numbers. */
object AppFonts {
    // One typeface across the whole app so login and post-login feel the same.
    val body = SoraFamily
    val display = SoraFamily
    val lighthouse = SoraFamily
}

/** Makes every Text inside default to Sora — the Lighthouse screens' typeface. */
@androidx.compose.runtime.Composable
fun LighthouseType(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides
            androidx.compose.material3.LocalTextStyle.current.merge(TextStyle(fontFamily = SoraFamily)),
        content = content,
    )
}

val CupertinoTypography = Typography(
    // Large Title — display face
    displayLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
    // Title 1 — display face
    headlineLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp),
    // Title 2 — display face
    headlineMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    // Headline — body face, semibold
    titleMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    // Body
    bodyLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    // Subhead
    bodyMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    // Footnote
    bodySmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    // Caption
    labelMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
