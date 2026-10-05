package com.shahaabapps.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(reducedMotion) {
        Celebrations.events.collect {
            type = it
            seed++
            visible = true
            delay(if (reducedMotion) 1000 else 1550)
            visible = false
            delay(320)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(340))) {
            CelebrationOverlay(
                type = type,
                accent = accent,
                destructive = destructive,
                gradient = gradient,
                seed = seed,
                reducedMotion = reducedMotion,
            )
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
    reducedMotion: Boolean,
) {
    val isSave = type == CelebrationType.SAVE
    // Theme colours drive everything; delete uses the destructive hue.
    val themeColors = if (isSave) (gradient.ifEmpty { listOf(accent) }) else listOf(destructive, destructive.copy(alpha = 0.8f))
    val bloom = themeColors.first()
    val confettiPalette = if (isSave) (gradient + accent) else listOf(destructive, destructive.copy(alpha = 0.65f))

    val particles = remember(seed) {
        val rnd = Random(seed * 7919 + 13)
        List(52) {
            Particle(
                angle = (rnd.nextFloat() * 2f * Math.PI).toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.7f,
                color = confettiPalette[rnd.nextInt(confettiPalette.size)],
                radius = 5f + rnd.nextFloat() * 9f,
                bar = rnd.nextBoolean(),
                spin = rnd.nextFloat() * 360f,
            )
        }
    }

    val progress = remember(seed) { Animatable(0f) }   // confetti + rings travel
    val pop = remember(seed) { Animatable(0f) }        // shield spring-in
    val emblem = remember(seed) { Animatable(0f) }     // check / minus reveal
    val shimmer = remember(seed) { Animatable(0f) }    // light sweep across shield

    LaunchedEffect(seed) {
        if (reducedMotion) pop.snapTo(1f)
        else pop.animateTo(1f, spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(seed) {
        if (reducedMotion) {
            emblem.snapTo(1f)
        } else {
            delay(140)
            emblem.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(seed) {
        if (!reducedMotion) progress.animateTo(1f, tween(1250, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(seed) {
        if (!reducedMotion) {
            delay(220)
            shimmer.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        // Backdrop bloom + radar rings + confetti, all full-screen.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.6f

            // Soft themed bloom growing from the centre.
            val bloomR = size.maxDimension * (0.25f + 0.55f * pop.value)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(bloom.copy(alpha = 0.40f), bloom.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = bloomR,
                ),
                radius = bloomR,
                center = center,
            )

            if (reducedMotion) return@Canvas

            // Radar pulse rings — the "watching" motif from the icon.
            for (i in 0..2) {
                val phase = (p - i * 0.16f).coerceIn(0f, 1f)
                if (phase <= 0f) continue
                val ringAlpha = (1f - phase) * 0.5f
                drawCircle(
                    color = accent.copy(alpha = if (isSave) ringAlpha else ringAlpha * 0.6f),
                    radius = maxR * phase,
                    center = center,
                    style = Stroke(width = (6f * (1f - phase)).coerceAtLeast(1.5f)),
                )
            }

            // Confetti burst flung outward with a little gravity.
            val fade = (1f - p).coerceIn(0f, 1f)
            particles.forEach { particle ->
                val dist = particle.speed * maxR * p
                val gravity = p * p * maxR * 0.34f
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

        // Hero shield badge that springs in, with a shimmer sweep and an emblem.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val scale = pop.value.coerceIn(0f, 1.18f)
                val badge = size.minDimension
                val unit = badge / 108f
                translate(left = (size.width - badge) / 2f, top = (size.height - badge) / 2f) {
                    // Scale the whole shield around its centre for the spring pop.
                    val cx = badge / 2f
                    val cy = badge / 2f
                    translate(left = cx * (1f - scale), top = cy * (1f - scale)) {
                        val shield = shieldPath(unit, scale)

                        // Drop shadow.
                        translate(top = 3f * unit) {
                            drawPath(shieldPath(unit, scale), color = Color.Black.copy(alpha = 0.18f))
                        }
                        // White badge with a gentle vertical sheen.
                        drawPath(
                            path = shield,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White, Color(0xFFF1F3F6)),
                                startY = 0f,
                                endY = badge,
                            ),
                        )
                        // Diagonal shimmer sweep, clipped to the shield.
                        if (shimmer.value > 0f) {
                            clipPath(shield) {
                                val sweepX = badge * (shimmer.value * 1.8f - 0.4f)
                                rotate(degrees = -20f, pivot = Offset(sweepX, badge / 2f)) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.55f * (1f - shimmer.value)),
                                        topLeft = Offset(sweepX - badge * 0.12f, -badge * 0.2f),
                                        size = Size(badge * 0.24f, badge * 1.4f),
                                        cornerRadius = CornerRadius(badge, badge),
                                    )
                                }
                            }
                        }

                        // Emblem inside the shield: check (save) or minus (delete).
                        val e = emblem.value.coerceIn(0f, 1f)
                        if (e > 0f) {
                            val stroke = Stroke(width = 7.5f * unit, cap = StrokeCap.Round)
                            val emblemColor = if (isSave) bloom else destructive
                            if (isSave) {
                                // Animated check mark.
                                val p1 = Offset(42f * unit, 53f * unit)
                                val mid = Offset(50f * unit, 61f * unit)
                                val p2 = Offset(67f * unit, 44f * unit)
                                val legA = (e / 0.4f).coerceIn(0f, 1f)
                                val legB = ((e - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                val check = Path().apply {
                                    moveTo(p1.x, p1.y)
                                    lineTo(p1.x + (mid.x - p1.x) * legA, p1.y + (mid.y - p1.y) * legA)
                                    if (legB > 0f) lineTo(mid.x + (p2.x - mid.x) * legB, mid.y + (p2.y - mid.y) * legB)
                                }
                                drawPath(check, color = emblemColor, style = stroke)
                            } else {
                                val half = 11f * unit * e
                                drawLine(
                                    color = emblemColor,
                                    start = Offset(54f * unit - half, 53f * unit),
                                    end = Offset(54f * unit + half, 53f * unit),
                                    strokeWidth = 7.5f * unit,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = if (isSave) "Saved" else "Removed",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp).alpha(pop.value.coerceIn(0f, 1f)),
            )
        }
    }
}

/** Builds the shield badge path in a [badge]-sized box, where [unit] = badge / 108. */
private fun DrawScope.shieldPath(unit: Float, @Suppress("UNUSED_PARAMETER") scale: Float): Path {
    fun x(v: Float) = v * unit
    fun y(v: Float) = v * unit
    return Path().apply {
        moveTo(x(54f), y(28f))
        cubicTo(x(49f), y(28f), x(43f), y(30f), x(38f), y(31f))
        cubicTo(x(34f), y(32f), x(32.5f), y(33.5f), x(32.5f), y(38f))
        lineTo(x(32.5f), y(56f))
        cubicTo(x(32.5f), y(69f), x(41f), y(78f), x(54f), y(83.5f))
        cubicTo(x(67f), y(78f), x(75.5f), y(69f), x(75.5f), y(56f))
        lineTo(x(75.5f), y(38f))
        cubicTo(x(75.5f), y(33.5f), x(74f), y(32f), x(70f), y(31f))
        cubicTo(x(65f), y(30f), x(59f), y(28f), x(54f), y(28f))
        close()
    }
}
