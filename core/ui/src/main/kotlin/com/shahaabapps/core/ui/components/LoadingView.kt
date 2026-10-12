package com.shahaabapps.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * A modern, branded loading spinner: a rotating "comet" arc drawn with a sweep
 * gradient in the accent colour. Falls back to a plain indicator when the user
 * has reduced motion enabled.
 */
@Composable
fun AppLoader(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = AppTheme.colors.accent,
    strokeWidth: Dp = 3.5.dp,
) {
    if (rememberReducedMotion()) {
        CircularProgressIndicator(color = color, strokeWidth = strokeWidth, modifier = modifier.size(size))
        return
    }
    val transition = rememberInfiniteTransition(label = "loader")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    Canvas(modifier = modifier.size(size)) {
        val inset = strokePx / 2f
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        rotate(angle) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.12f), color),
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

/** A centered [AppLoader] with an optional, gently pulsing label. */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppLoader(size = 40.dp)
        if (label != null) {
            val transition = rememberInfiniteTransition(label = "loadingLabel")
            val reduce = rememberReducedMotion()
            val alpha by transition.animateFloat(
                initialValue = if (reduce) 1f else 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                label = "labelAlpha",
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel.copy(alpha = if (reduce) 1f else alpha),
            )
        }
    }
}

/** A small inline [AppLoader] for trailing slots (e.g. a list row that is syncing). */
@Composable
fun InlineSpinner(modifier: Modifier = Modifier, color: Color = AppTheme.colors.accent) {
    AppLoader(modifier = modifier, size = 18.dp, color = color, strokeWidth = 2.dp)
}
