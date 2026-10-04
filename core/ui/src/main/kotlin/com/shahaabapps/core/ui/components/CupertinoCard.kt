package com.shahaabapps.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Shapes
import com.shahaabapps.core.ui.theme.Spacing

/**
 * A rounded, subtly elevated content card in the modern iOS style.
 */
@Composable
fun CupertinoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = Shapes.card,
        color = AppTheme.colors.card,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) { content() }
    }
}
