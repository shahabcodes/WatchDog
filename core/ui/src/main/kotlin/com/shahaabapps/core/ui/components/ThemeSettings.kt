package com.shahaabapps.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AccentPalette
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.core.ui.theme.ThemeMode

/** The appearance + accent controls, shared by both apps' settings screens. */
@Composable
fun ThemeSettingsSection(
    mode: ThemeMode,
    palette: AccentPalette,
    onModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (AccentPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.secondaryLabel,
        )
        SegmentedControl(
            options = ThemeMode.entries,
            selected = mode,
            label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = onModeChange,
        )

        Text(
            text = "ACCENT",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AccentPalette.entries.forEach { option ->
                AccentSwatch(
                    palette = option,
                    selected = option == palette,
                    onClick = { onPaletteChange(option) },
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(
                if (isSelected) AppTheme.colors.card else Color.Transparent,
                label = "segbg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        AppTheme.colors.secondaryLabel
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.gradient))
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
        )
        Text(
            text = palette.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
