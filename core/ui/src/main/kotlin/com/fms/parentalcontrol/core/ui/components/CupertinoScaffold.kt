package com.fms.parentalcontrol.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fms.parentalcontrol.core.ui.theme.AppTheme
import com.fms.parentalcontrol.core.ui.theme.Spacing

/**
 * A screen scaffold with the iOS "large title" that sits inline at the top of
 * the scrollable content (not a centered app bar).
 */
@Composable
fun CupertinoScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.groupedBackground,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier.padding(
                    start = Spacing.listHorizontal,
                    end = Spacing.listHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.md,
                ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            content(PaddingValues(0.dp))
        }
    }
}
