package com.shahaabapps.shield.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Shield app mark: a gold tile with a navy ring overlaid by a white dashed
 * ring (the "radar" emblem from the lighthouse prototype).
 */
@Composable
fun ShieldMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val navy = Color(0xFF012169)
    val gold = Color(0xFFF5B83D)
    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(gold),
    ) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension * 0.3f
        val w = this.size.minDimension * 0.12f
        drawCircle(color = navy, radius = r, center = c, style = Stroke(width = w))
        drawCircle(
            color = Color.White,
            radius = r,
            center = c,
            style = Stroke(
                width = w,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(this.size.minDimension * 0.1f, this.size.minDimension * 0.16f),
                ),
            ),
        )
    }
}
