package com.shahaabapps.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class CelebrationType { SAVE, DELETE }

/** A tiny app-wide event bus for the full-screen save/delete celebration. */
object Celebrations {
    private val _events = MutableSharedFlow<CelebrationType>(extraBufferCapacity = 8)
    val events: SharedFlow<CelebrationType> = _events.asSharedFlow()

    fun saved() { _events.tryEmit(CelebrationType.SAVE) }
    fun deleted() { _events.tryEmit(CelebrationType.DELETE) }
}

/** Hosts the full-screen celebration overlay above [content]. */
@Composable
fun CelebrationHost(content: @Composable () -> Unit) {
    var type by remember { mutableStateOf(CelebrationType.SAVE) }
    var visible by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf(0) }
    val accent = AppTheme.colors.accent
    val destructive = AppTheme.colors.destructive
    val gradient = AppTheme.palette.gradient

    LaunchedEffect(Unit) {
        Celebrations.events.collect {
            type = it
            seed++
            visible = true
            delay(1300)
            visible = false
            delay(300)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(320))) {
            CelebrationOverlay(type = type, accent = accent, destructive = destructive, gradient = gradient, seed = seed)
        }
    }
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val radius: Float,
    val bar: Boolean,
    val spin: Float,
)

@Composable
private fun CelebrationOverlay(
    type: CelebrationType,
    accent: Color,
    destructive: Color,
    gradient: List<Color>,
    seed: Int,
) {
    val isSave = type == CelebrationType.SAVE
    val palette = if (isSave) (gradient + accent) else listOf(destructive, destructive.copy(alpha = 0.7f))

    val particles = remember(seed) {
        val rnd = Random(seed * 7919 + 13)
        List(44) {
            Particle(
                angle = (rnd.nextFloat() * 2f * Math.PI).toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.65f,
                color = palette[rnd.nextInt(palette.size)],
                radius = 5f + rnd.nextFloat() * 9f,
                bar = rnd.nextBoolean(),
                spin = rnd.nextFloat() * 360f,
            )
        }
    }

    val progress = remember(seed) { Animatable(0f) }
    val pop = remember(seed) { Animatable(0f) }
    LaunchedEffect(seed) {
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(seed) {
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f * (1f - progress.value))),
        contentAlignment = Alignment.Center,
    ) {
        // Confetti / particle burst
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension * 0.55f
            val fade = (1f - p).coerceIn(0f, 1f)
            particles.forEach { particle ->
                val dist = particle.speed * maxR * p
                val gravity = p * p * maxR * 0.35f
                val x = center.x + cos(particle.angle) * dist
                val y = center.y + sin(particle.angle) * dist + gravity
                val col = particle.color.copy(alpha = fade)
                if (particle.bar) {
                    rotate(particle.spin + p * 220f, Offset(x, y)) {
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(x - particle.radius, y - particle.radius / 2.4f),
                            size = Size(particle.radius * 2f, particle.radius * 0.9f),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                } else {
                    drawCircle(color = col, radius = particle.radius * (1f - 0.25f * p), center = Offset(x, y))
                }
            }
        }

        // Center medallion that pops in
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .scale(pop.value.coerceAtMost(1.15f))
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSave) Brush.linearGradient(gradient) else Brush.linearGradient(listOf(destructive, destructive)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSave) Icons.Filled.Check else Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = if (isSave) "Saved" else "Removed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp).alpha(pop.value.coerceIn(0f, 1f)),
            )
        }
    }
}
