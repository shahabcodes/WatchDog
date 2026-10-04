package com.shahaabapps.watchdog.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoScaffold
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val LIMIT_OPTIONS = listOf(null, 15, 30, 60, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailScreen(
    onOpenGeofences: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AppRuleRow?>(null) }

    CupertinoScaffold(title = state.childName.ifBlank { "Child" }, modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                CupertinoCard(onClick = onOpenGeofences) {
                    Text("Places & geofences", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Get alerts when they enter or leave a place",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
            item {
                Text(
                    text = "APPS — tap to set a daily limit or block",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
                )
            }
            if (state.rows.isEmpty()) {
                item { InventoryHint() }
            } else {
                items(state.rows, key = { it.app.packageName }) { row ->
                    CupertinoCard(onClick = { editing = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.app.label.ifBlank { row.app.packageName },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = ruleSummary(row.rule),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.secondaryLabel,
                                )
                            }
                            if (row.rule?.blocked == true) {
                                StatusChip(text = "Blocked", color = AppTheme.colors.destructive)
                            } else if (row.rule?.dailyLimitMinutes != null) {
                                StatusChip(text = "Limited", color = AppTheme.colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
        ) {
            RuleEditor(
                row = current,
                onSave = { limit, blocked ->
                    viewModel.saveRule(current.app, limit, blocked, current.rule)
                    editing = null
                },
                onRemove = {
                    viewModel.clearRule(current.app.packageName)
                    editing = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    row: AppRuleRow,
    onSave: (dailyLimitMinutes: Int?, blocked: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var blocked by remember { mutableStateOf(row.rule?.blocked ?: false) }
    var limit by remember { mutableStateOf(row.rule?.dailyLimitMinutes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = row.app.label.ifBlank { row.app.packageName },
            style = MaterialTheme.typography.headlineMedium,
        )

        CupertinoCard {
            CupertinoListRow(
                title = "Block completely",
                subtitle = "Always off-limits",
                showDivider = false,
                trailing = {
                    CupertinoSwitch(checked = blocked, onCheckedChange = { blocked = it })
                },
            )
        }

        if (!blocked) {
            Text(
                text = "Daily limit",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LIMIT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = limit == option,
                        onClick = { limit = option },
                        label = { Text(limitLabel(option)) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        CupertinoPrimaryButton(
            text = "Save",
            onClick = { onSave(if (blocked) null else limit, blocked) },
        )
        if (row.rule != null) {
            CupertinoTextButton(text = "Remove rule", onClick = onRemove, destructive = true)
        }
    }
}

@Composable
private fun InventoryHint() {
    CupertinoCard {
        Text(text = "Waiting for app list", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Open Shield on the child's phone — its apps will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun limitLabel(minutes: Int?): String = when (minutes) {
    null -> "No limit"
    in 60..Int.MAX_VALUE -> "${minutes / 60}h"
    else -> "${minutes}m"
}

private fun ruleSummary(rule: AppRule?): String = when {
    rule == null -> "No limit"
    rule.blocked -> "Blocked"
    rule.dailyLimitMinutes != null -> "${limitLabel(rule.dailyLimitMinutes)} per day"
    else -> "No limit"
}
