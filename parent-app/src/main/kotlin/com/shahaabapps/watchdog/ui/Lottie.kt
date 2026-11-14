package com.shahaabapps.watchdog.ui

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.R

/**
 * The app's animations (from LottieFiles, under the Lottie Simple License; see
 * About). [darkRes] is a recoloured copy for dark backgrounds. [still] is the
 * frame shown when the phone has animations turned off.
 */
enum class Anim(@RawRes val res: Int, @RawRes val darkRes: Int? = null, val still: Float = 0.5f) {
    CORGI_WALK(R.raw.anim_corgi_walk),
    CORGI_SLEEP(R.raw.anim_corgi_sleep),
    CORGI_BOX(R.raw.anim_corgi_box),
    CAMERA(R.raw.anim_camera),
    PAW_PIN(R.raw.anim_paw_pin),
    CONFETTI_CHECK(R.raw.anim_confetti_check, still = 1f),
    CHECK(R.raw.anim_check, still = 1f),
    PAW_LOADER(R.raw.anim_paw_loader, darkRes = R.raw.anim_paw_loader_dark),
}

/** True when the phone's animations are off (Accessibility › Remove animations). */
@Composable
fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Plays [anim]: looping, or once when [loop] is false ([onFinished] then runs).
 * With animations off it shows a still frame (and still calls [onFinished]).
 */
@Composable
fun LottieAnim(
    anim: Anim,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    onFinished: (() -> Unit)? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val res = if (dark) anim.darkRes ?: anim.res else anim.res
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val off = animationsOff()
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !off,
        iterations = if (loop) LottieConstants.IterateForever else 1,
    )
    val finished by rememberUpdatedState(onFinished)
    if (!loop && composition != null) {
        LaunchedEffect(off, progress >= 1f) {
            if (off || progress >= 1f) {
                // Rest on the last frame for a moment (longer when it's a still).
                kotlinx.coroutines.delay(if (off) 900 else 350)
                finished?.invoke()
            }
        }
    }
    LottieAnimation(
        composition = composition,
        progress = { if (off) anim.still else progress },
        contentScale = ContentScale.Fit,
        enableMergePaths = true,
        modifier = modifier,
    )
}

/** An empty state: an animation over a title and a short explanation. */
@Composable
fun AnimatedEmpty(
    anim: Anim,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        LottieAnim(anim, Modifier.size(size))
        Text(title, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Watch Dog's loading view: the paw loader over an optional label. */
@Composable
fun PawLoading(label: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LottieAnim(Anim.PAW_LOADER, Modifier.size(72.dp))
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.secondaryLabel)
        }
    }
}

/** Who made each animation (attribution is optional under the licence, but kind). */
private val ANIM_CREDITS = listOf(
    "Cute Doggie" to "XAV",
    "Puppy sleeping" to "Tushar",
    "Cute Dog" to "Vitra",
    "Digital Camera" to "Mustafizur Rahman",
    "Dog pet map location marker" to "Yohan",
    "Action completed with confetti" to "Mio EN",
    "Check Mark - Success" to "Travis Gregory",
    "Paw Loader" to "Sebastian Pallero Oría",
)

private const val LOTTIE_LICENSE = "Lottie Simple License (FL 9.13.21)\n" +
    "Copyright © 2021 Design Barn Inc.\n\n" +
    "Permission is hereby granted, free of charge, to any person obtaining a copy of the public animation files " +
    "available for download at the LottieFiles site (“Files”) to download, reproduce, modify, publish, distribute, " +
    "publicly display, and publicly digitally perform such Files, including for commercial purposes, provided that " +
    "any display, publication, performance, or distribution of Files must contain (and be subject to) the same terms " +
    "and conditions of this license. Modifications to Files are deemed derivative works and must also be expressly " +
    "distributed under the same terms and conditions of this license. You may not purport to impose any additional " +
    "or different terms or conditions on, or apply any technical measures that restrict exercise of, the rights " +
    "granted under this license. This license does not include the right to collect or compile Files from " +
    "LottieFiles to replicate or develop a similar or competing service. Use of Files without attributing the " +
    "creator(s) of the Files is permitted under this license, though attribution is strongly encouraged. If " +
    "attributions are included, such attributions should be visible to the end user.\n\n" +
    "FILES ARE PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE " +
    "WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. EXCEPT TO THE EXTENT " +
    "REQUIRED BY APPLICABLE LAW, IN NO EVENT WILL THE CREATOR(S) OF FILES OR DESIGN BARN, INC. BE LIABLE ON ANY " +
    "LEGAL THEORY FOR ANY SPECIAL, INCIDENTAL, CONSEQUENTIAL, PUNITIVE, OR EXEMPLARY DAMAGES ARISING OUT OF THIS " +
    "LICENSE OR THE USE OF SUCH FILES."

/** "Animations from LottieFiles" in About; tap for the artists and the licence. */
@Composable
fun AnimationCredits() {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        "Animations from LottieFiles · Lottie Simple License",
        style = MaterialTheme.typography.bodySmall,
        color = AppTheme.colors.accent,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable { open = true }
            .padding(vertical = 2.dp),
    )
    if (open) {
        com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { open = false },
            title = "Animations",
            dismissText = "Close",
        ) {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ANIM_CREDITS.forEach { (name, by) ->
                    Text("$name by $by", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    LOTTIE_LICENSE,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
