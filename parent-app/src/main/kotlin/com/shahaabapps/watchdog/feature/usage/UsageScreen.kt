package com.shahaabapps.watchdog.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Brand
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val maxMinutes = (state.rows.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Hero(
            title = "Screen time",
            subtitle = "Today",
            actions = {
                HeroIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
        ) {
            Text(
                formatDuration(state.totalMinutes),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.loading && state.rows.isEmpty()) {
                LoadingView(label = "Loading screen time…")
            } else if (state.rows.isEmpty()) {
                CupertinoCard {
                    Text("No usage yet", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Once Shield has Usage Access and the child uses apps, screen time appears here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            } else {
                Text("By app", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.secondaryLabel)
                state.rows.forEach { row -> UsageBar(row, maxMinutes) }
            }
        }
    }
}

@Composable
private fun UsageBar(row: UsageRow, maxMinutes: Int) {
    CupertinoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppTheme.colors.separator),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (row.minutes.toFloat() / maxMinutes).coerceIn(0.03f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brand.accentGradient),
                    )
                }
            }
            Text(
                formatDuration(row.minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
