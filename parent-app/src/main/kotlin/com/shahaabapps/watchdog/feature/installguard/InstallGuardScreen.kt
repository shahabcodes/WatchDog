package com.shahaabapps.watchdog.feature.installguard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun InstallGuardScreen(
    onBack: () -> Unit,
    viewModel: InstallGuardViewModel = hiltViewModel(),
) {
    val guard by viewModel.guard.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AppSort.SELECTED_FIRST) }
    var newPackage by remember { mutableStateOf("") }

    // Live set for the checkmarks (updates instantly on toggle).
    val selectedSet = remember(guard.packages) { guard.packages.toHashSet() }
    // The "selected" grouping is SNAPSHOTTED (keyed on sort/query, not on packages)
    // so toggling a row never re-sorts/reorders the list — that reorder was the lag.
    val orderSelected = remember(sort, query) {
        if (sort == AppSort.SELECTED_FIRST) guard.packages.toHashSet() else emptySet()
    }
    val visibleApps = remember(apps, query, sort, orderSelected) {
        val q = query.trim().lowercase()
        val name = { a: InstalledApp -> a.label.ifBlank { a.packageName }.lowercase() }
        val filtered = apps.filter {
            q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
        when (sort) {
            AppSort.SELECTED_FIRST -> filtered.sortedWith(
                compareByDescending<InstalledApp> { it.packageName in orderSelected }.thenBy(name),
            )
            AppSort.NAME_AZ -> filtered.sortedBy(name)
            AppSort.NAME_ZA -> filtered.sortedByDescending(name)
            AppSort.PACKAGE -> filtered.sortedBy { it.packageName.lowercase() }
        }
    }
    val typedPackages = remember(guard.packages, apps) {
        guard.packages.filter { pkg -> apps.none { it.packageName == pkg } }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground),
    ) {
        GuardianHeader(title = "App installs", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl),
        ) {
            item(key = "enable") {
                CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    CupertinoListRow(
                        title = "Control sideloaded installs",
                        subtitle = "Block apps installed or updated from outside the Play Store",
                        showDivider = false,
                        trailing = { Switch(checked = guard.enabled, onCheckedChange = viewModel::setEnabled) },
                    )
                }
            }

            if (guard.enabled) {
                item(key = "mode") {
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        SectionHeader("Mode", modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs))
                        CupertinoCard {
                            CupertinoListRow(
                                title = "Allow only the apps I pick",
                                subtitle = if (guard.allowlist) "Everything else is blocked from sideloading"
                                else "Off: instead, block only the apps I pick below",
                                showDivider = false,
                                trailing = { Switch(checked = guard.allowlist, onCheckedChange = viewModel::setAllowlistMode) },
                            )
                        }
                    }
                }

                item(key = "listHeader") {
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                        SectionHeader(
                            text = if (guard.allowlist) "Allowed to install" else "Blocked from installing",
                            modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
                        )
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                        )
                        SortPills(selected = sort, onSelect = { sort = it })
                    }
                }

                if (visibleApps.isEmpty()) {
                    item(key = "empty") {
                        RowCard(top = true, bottom = true, horizontal = true) {
                            CupertinoListRow(
                                title = if (apps.isEmpty()) "No apps reported yet" else "No matches",
                                subtitle = if (apps.isEmpty()) "Pick apps once the child's app list has synced." else "Try a different search.",
                                showDivider = false,
                            )
                        }
                    }
                } else {
                    items(visibleApps, key = { it.packageName }) { app ->
                        RowCard(horizontal = true, modifier = Modifier.animateItem()) {
                            AppToggleRow(
                                icon = app.icon,
                                label = app.label.ifBlank { app.packageName },
                                sub = app.packageName,
                                checked = app.packageName in selectedSet,
                                onCheckedChange = { viewModel.togglePackage(app.packageName, it) },
                            )
                        }
                    }
                }

                if (typedPackages.isNotEmpty()) {
                    item(key = "typedHeader") {
                        SectionHeader(
                            "Added by package name",
                            modifier = Modifier.padding(start = Spacing.lg + Spacing.md, top = Spacing.md, bottom = Spacing.xs),
                        )
                    }
                    items(typedPackages, key = { "typed_$it" }) { pkg ->
                        RowCard(horizontal = true) {
                            AppToggleRow(
                                icon = null,
                                label = pkg,
                                sub = null,
                                checked = true,
                                onCheckedChange = { viewModel.togglePackage(pkg, it) },
                            )
                        }
                    }
                }

                item(key = "addByName") {
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                        OutlinedTextField(
                            value = newPackage,
                            onValueChange = { newPackage = it.trim() },
                            singleLine = true,
                            label = { Text("Add a package name") },
                            placeholder = { Text("com.android.chrome") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = {
                                if (newPackage.isNotBlank()) { viewModel.togglePackage(newPackage.trim(), true); newPackage = "" }
                            },
                            enabled = newPackage.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(if (guard.allowlist) "Allow this package" else "Block this package") }
                    }
                }

                item(key = "note") {
                    Text(
                        buildString {
                            append("Matches by the app's real package name from the downloaded APK, so a renamed copy can't slip through. ")
                            if (guard.allowlist) {
                                append("Only these apps can be installed/updated from unknown sources; other new sideloads are blocked at download. Apps already on the phone (and all system apps) keep working. ")
                            } else {
                                append("These apps are blocked from being installed/updated from unknown sources, and are stopped from opening if already sideloaded. ")
                            }
                            append("Installs from a computer (USB/adb) can't be stopped this way — you'll be notified instead. Needs All-files access on the child device.")
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = Spacing.lg + Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * A single row rendered on the themed card colour so consecutive lazy items read
 * as one continuous card, with the top/bottom items rounded.
 */
@Composable
private fun RowCard(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (top) 14.dp else 0.dp,
        topEnd = if (top) 14.dp else 0.dp,
        bottomStart = if (bottom) 14.dp else 0.dp,
        bottomEnd = if (bottom) 14.dp else 0.dp,
    )
    Column(
        modifier = modifier
            .padding(horizontal = if (horizontal) Spacing.lg else 0.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.card),
    ) { content() }
}

/** The sort orders offered as pills above the app list. */
private enum class AppSort(val label: String) {
    SELECTED_FIRST("Selected first"),
    NAME_AZ("Name A–Z"),
    NAME_ZA("Name Z–A"),
    PACKAGE("Package"),
}

/** A horizontally scrollable row of single-select sort pills. */
@Composable
private fun SortPills(selected: AppSort, onSelect: (AppSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AppSort.entries.forEach { sort ->
            Pill(text = sort.label, selected = sort == selected, onClick = { onSelect(sort) })
        }
    }
}

/** A rounded, tappable filter chip that fills in when selected. */
@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) AppTheme.colors.accent else AppTheme.colors.card,
        label = "pillBg",
    )
    val fg by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        label = "pillFg",
    )
    val border by animateColorAsState(
        if (selected) AppTheme.colors.accent else AppTheme.colors.separator,
        label = "pillBorder",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

/** A rounded, icon-led search bar with a clear button. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppTheme.colors.secondaryLabel) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = AppTheme.colors.secondaryLabel)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.card,
            unfocusedContainerColor = AppTheme.colors.card,
            focusedBorderColor = AppTheme.colors.accent,
            unfocusedBorderColor = AppTheme.colors.separator,
        ),
        modifier = modifier,
    )
}

/** An app row: launcher icon, name + package, and a toggle. */
@Composable
private fun AppToggleRow(
    icon: String?,
    label: String,
    sub: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 8.dp),
    ) {
        AppIcon(base64 = icon, label = label, size = 38.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
