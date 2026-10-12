package com.shahaabapps.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * An About section for the Settings screen: app name, tagline, the installed
 * version (read at runtime), and an in-app release history ([Changelog]).
 */
@Composable
fun AboutSection(
    appName: String,
    tagline: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionLabel = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(info)
            "Version ${info.versionName} ($code)"
        }.getOrDefault("Version —")
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.secondaryLabel,
        )
        CupertinoCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(appName, style = MaterialTheme.typography.titleLarge)
                Text(
                    tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                Text(
                    versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Maps © OpenFreeMap · OpenStreetMap contributors",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Text(
            text = "What's new",
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        CupertinoCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Changelog.entries.forEach { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Version ${note.version}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        note.highlights.forEach { line ->
                            Row {
                                Text(
                                    "•  ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.secondaryLabel,
                                )
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.secondaryLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
