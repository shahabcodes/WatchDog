package com.shahaabapps.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme

/**
 * A modern iOS-style segmented control: a translucent track with a frosted
 * sliding thumb and a soft accent glow on the selected segment.
 */
@Composable
fun CupertinoSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 38.dp,
) {
    val count = options.size.coerceAtLeast(1)
    val accent = AppTheme.colors.accent

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.separator.copy(alpha = 0.7f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val segWidth = maxWidth / count
            val thumbOffset by animateDpAsState(
                targetValue = segWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 520f),
                label = "segThumb",
            )

            // Sliding thumb
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = AppTheme.colors.card,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(0.5.dp, accent.copy(alpha = 0.25f)),
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(segWidth)
                    .fillMaxHeight(),
            ) {}

            Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
                options.forEachIndexed { i, label ->
                    val selected = i == selectedIndex
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) accent else AppTheme.colors.secondaryLabel,
                        )
                    }
                }
            }
        }
    }
}
