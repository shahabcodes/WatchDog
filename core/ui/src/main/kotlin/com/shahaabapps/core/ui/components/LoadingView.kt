package com.shahaabapps.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/** A centered spinner with an optional label, in the app's accent color. */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(
            color = AppTheme.colors.accent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
        }
    }
}

/** A small inline spinner for trailing slots (e.g. a list row that is syncing). */
@Composable
fun InlineSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        color = AppTheme.colors.accent,
        strokeWidth = 2.dp,
        modifier = modifier.size(18.dp),
    )
}
