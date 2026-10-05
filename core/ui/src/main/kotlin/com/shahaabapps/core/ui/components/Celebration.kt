package com.shahaabapps.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.theme.AccentPalette
import com.shahaabapps.core.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class CelebrationType { SAVE, DELETE }

/** A tiny app-wide event bus for full-screen save/delete celebrations. */
object Celebrations {
    private val _events = MutableSharedFlow<CelebrationType>(extraBufferCapacity = 8)
    val events: SharedFlow<CelebrationType> = _events.asSharedFlow()

    fun saved() { _events.tryEmit(CelebrationType.SAVE) }
    fun deleted() { _events.tryEmit(CelebrationType.DELETE) }
}

/**
 * Hosts the full-screen celebration overlay above [content]. Shows a flag
 * animation for country themes, otherwise a check/remove burst.
 */
@Composable
fun CelebrationHost(content: @Composable () -> Unit) {
    var type by remember { mutableStateOf(CelebrationType.SAVE) }
    var visible by remember { mutableStateOf(false) }
    val palette = AppTheme.palette

    LaunchedEffect(Unit) {
        Celebrations.events.collect {
            type = it
            visible = true
            delay(1500)
            visible = false
            delay(300)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
            CelebrationOverlay(type = type, palette = palette)
        }
    }
}

@Composable
private fun CelebrationOverlay(type: CelebrationType, palette: AccentPalette) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }

    if (palette.isCountry && palette.flag != null) {
        FlagCelebration(flag = palette.flag!!, gradient = palette.gradient, intro = intro.value)
    } else {
        CheckCelebration(type = type, accent = AppTheme.colors.accent, destructive = AppTheme.colors.destructive, intro = intro.value)
    }
}

@Composable
private fun FlagCelebration(flag: String, gradient: List<Color>, intro: Float) {
    val transition = rememberInfiniteTransition(label = "flag")
    val wave by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wave",
    )
    val fly by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "fly",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient.map { it.copy(alpha = 0.35f * intro) })),
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth
        val h = maxHeight
        // Flying flags across the screen.
        listOf(0.18f, 0.42f, 0.7f, 0.85f).forEachIndexed { i, y ->
            val progress = ((fly + i * 0.27f) % 1.2f) - 0.2f
            Text(
                text = flag,
                fontSize = 34.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = w * progress, y = h * y)
                    .rotate((wave - 0.5f) * 24f)
                    .alpha(0.9f),
            )
        }
        // Big waving flag in the centre.
        Text(
            text = flag,
            fontSize = 96.sp,
            modifier = Modifier
                .scale(0.5f + 0.5f * intro)
                .rotate((wave - 0.5f) * 14f),
        )
    }
}

@Composable
private fun CheckCelebration(type: CelebrationType, accent: Color, destructive: Color, intro: Float) {
    val color = if (type == CelebrationType.SAVE) accent else destructive
    val transition = rememberInfiniteTransition(label = "pulse")
    val ring by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)), label = "ring",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f * intro)),
        contentAlignment = Alignment.Center,
    ) {
        // Expanding ring
        Box(
            modifier = Modifier
                .size((120 + ring * 120).dp)
                .scale(1f)
                .alpha((1f - ring) * 0.5f * intro)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.25f)),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(0.5f + 0.5f * intro)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (type == CelebrationType.SAVE) Icons.Filled.Check else Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
            Text(
                text = if (type == CelebrationType.SAVE) "Saved" else "Removed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp).alpha(intro),
            )
        }
    }
}
