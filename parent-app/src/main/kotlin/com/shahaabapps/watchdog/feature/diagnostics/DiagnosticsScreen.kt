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

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        GuardianHeader(title = "Diagnostics", onBack = onBack)
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CupertinoCard {
                CupertinoListRow(
                    title = "Collect diagnostics",
                    subtitle = "Crash reports and anonymous breadcrumbs to help fix bugs. No locations or content.",
                    showDivider = false,
                    trailing = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled) },
                )
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CupertinoPrimaryButton(
                    text = "Copy",
                    onClick = { clipboard.setText(AnnotatedString(viewModel.exportText())) },
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.events.isEmpty()) {
                item {
                    Text(
                        "No events yet. Reproduce the issue, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
            items(state.events) { e ->
                val color = when (e.level) {
                    DiagLevel.ERROR -> AppTheme.colors.destructive
                    DiagLevel.WARN -> AppTheme.colors.warn
                    DiagLevel.INFO -> AppTheme.colors.secondaryLabel
                }
                Text(
                    "${clock.format(Date(e.atMillis))}  ${e.level}  ${e.tag}: ${e.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                )
            }
        }
    }
}
