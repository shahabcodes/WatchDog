package com.shahaabapps.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Home's card "reveal": fade in with a 20dp upward slide over 320ms, once per
 * appearance. [delayMillis] staggers a list (e.g. 40ms per item). Drawn through
 * graphicsLayer so it never recomposes; skipped entirely under reduced motion.
 */
@Composable
fun Modifier.dashReveal(delayMillis: Int = 0): Modifier {
    if (rememberReducedMotion()) return this
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(durationMillis = 320, delayMillis = delayMillis, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 20.dp.toPx()
    }
}
