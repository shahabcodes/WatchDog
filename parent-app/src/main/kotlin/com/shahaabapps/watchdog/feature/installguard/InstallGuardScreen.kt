package com.shahaabapps.watchdog.feature.installguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shahaabapps.core.model.InstalledApp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var selectedFirst by remember { mutableStateOf(true) }
    val visibleApps = remember(apps, guard.packages, query, selectedFirst) {
        val q = query.trim().lowercase()
        apps
            .filter {
                q.isEmpty() ||
                    it.label.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }
            .sortedWith(
                compareByDescending<InstalledApp> { if (selectedFirst) it.packageName in guard.packages else false }
                    .thenBy { it.label.ifBlank { it.packageName }.lowercase() },
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        GuardianHeader(title = "App installs", onBack = onBack)

        CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            CupertinoListRow(
                title = "Control sideloaded installs",
                subtitle = "Block apps installed or updated from outside the Play Store",
                showDivider = false,
                trailing = {
                    Switch(checked = guard.enabled, onCheckedChange = viewModel::setEnabled)
                },
            )
        }

        if (guard.enabled) {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                SectionHeader(text = "Mode", modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs))
                CupertinoCard {
                    CupertinoListRow(
                        title = "Allow only the apps I pick",
                        subtitle = if (guard.allowlist) {
                            "Everything else is blocked from sideloading"
                        } else {
                            "Off: instead, block only the apps I pick below"
                        },
                        showDivider = false,
                        trailing = {
                            Switch(checked = guard.allowlist, onCheckedChange = viewModel::setAllowlistMode)
                        },
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                SectionHeader(
                    text = if (guard.allowlist) "Allowed to install" else "Blocked from installing",
                    modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                )
                CupertinoCard {
                    CupertinoListRow(
                        title = "Show selected first",
                        showDivider = true,
                        trailing = { Switch(checked = selectedFirst, onCheckedChange = { selectedFirst = it }) },
                    )
                    if (visibleApps.isEmpty()) {
                        CupertinoListRow(
                            title = if (apps.isEmpty()) "No apps reported yet" else "No matches",
                            subtitle = if (apps.isEmpty()) {
                                "Pick apps here once the child device has synced its app list."
                            } else {
                                "Try a different search."
                            },
                            showDivider = false,
                        )
                    } else {
                        visibleApps.forEachIndexed { index, app ->
                            val selected = app.packageName in guard.packages
                            CupertinoListRow(
                                title = app.label.ifBlank { app.packageName },
                                subtitle = app.packageName,
                                showDivider = index < visibleApps.lastIndex,
                                trailing = {
                                    Switch(
                                        checked = selected,
                                        onCheckedChange = { viewModel.togglePackage(app.packageName, it) },
                                    )
                                },
                            )
                        }
                    }
                }
                Text(
                    "Matches by the app's real package name read from the downloaded APK, so a " +
                        "renamed copy can't slip through. Installs from a computer (USB/adb) can't be " +
                        "stopped this way — you'll be notified instead. Requires All-files access on the child device.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.sm, start = Spacing.md, end = Spacing.md),
                )
            }
        }
    }
}
