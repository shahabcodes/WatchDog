package com.shahaabapps.watchdog.feature.diagnostics

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.firebase.diagnostics.DiagLevel
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.core.ui.tour.tourTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val clock = remember { SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        GuardianHeader(title = "Diagnostics", onBack = onBack)
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CupertinoCard(modifier = Modifier.tourTarget("diag.collect")) {
                CupertinoListRow(
                    title = "Collect diagnostics",
                    subtitle = "Errors, warnings and actions from your family's phones, with date and time, to help fix problems. No locations or content.",
                    showDivider = false,
                    trailing = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled) },
                )
            }
            // Whose diagnostics: this phone, my account, or a child's phone.
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.tourTarget("diag.sources"),
            ) {
                items(state.sources) { src ->
                    androidx.compose.material3.FilterChip(
                        selected = src.key == state.selected,
                        onClick = { viewModel.select(src.key) },
                        label = { Text(src.label) },
                    )
                }
            }
            if (state.loading) Text("Loading…", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.destructive) }
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CupertinoTextButton(
                    text = "Refresh",
                    onClick = viewModel::refresh,
                    modifier = Modifier.weight(1f).tourTarget("diag.refresh"),
                )
                CupertinoPrimaryButton(
                    text = "Copy",
                    onClick = { clipboard.setText(AnnotatedString(viewModel.exportText())) },
                    modifier = Modifier.weight(1f).tourTarget("diag.copy"),
                )
                CupertinoTextButton(
                    text = "Share",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportText())
                        }
                        context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                    },
                    modifier = Modifier.weight(1f).tourTarget("diag.share"),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.rows.isEmpty() && !state.loading) {
                item {
                    Text(
                        "No events yet. Reproduce the issue, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
            items(state.rows) { e ->
                val color = when (e.level) {
                    DiagLevel.ERROR -> AppTheme.colors.destructive
                    DiagLevel.WARN -> AppTheme.colors.warn
                    DiagLevel.INFO -> AppTheme.colors.secondaryLabel
                }
                Text(
                    "${clock.format(Date(e.atMillis))}  ${e.level}  ${e.where}  [${e.tag}] ${e.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                )
            }
        }
    }
}
