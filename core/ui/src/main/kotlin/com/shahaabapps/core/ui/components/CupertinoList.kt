package com.shahaabapps.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Shapes
import com.shahaabapps.core.ui.theme.Spacing

/**
 * A grouped, inset-rounded list section in the iOS Settings style, with an
 * optional uppercase header and footer caption.
 */
@Composable
fun CupertinoListSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(
                    start = Spacing.listHorizontal,
                    end = Spacing.listHorizontal,
                    bottom = Spacing.sm,
                ),
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.listHorizontal)
                .clip(Shapes.card)
                .background(AppTheme.colors.card),
            content = { content() },
        )
        if (footer != null) {
            Text(
                text = footer,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(
                    start = Spacing.listHorizontal,
                    end = Spacing.listHorizontal,
                    top = Spacing.sm,
                ),
            )
        }
    }
}

/**
 * A single row within a [CupertinoListSection]. Supports a leading icon, title,
 * optional subtitle/value, and a trailing slot (e.g. chevron or switch).
 */
@Composable
fun CupertinoListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = Spacing.rowMinHeight)
                .padding(horizontal = Spacing.md, vertical = 10.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(
                color = AppTheme.colors.separator,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
    }
}

/** An iOS-style chevron shape placeholder spacer used as a trailing disclosure. */
@Composable
fun CupertinoDisclosure(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colors.secondaryLabel,
        modifier = modifier,
    )
}
