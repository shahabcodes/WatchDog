package com.shahaabapps.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.theme.AppTheme

/**
 * A flat iOS stat tile (Browse Safe `view_stat`): a big bold value, a small
 * label, and an optional icon, on a rounded surface-variant card.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: androidx.compose.ui.graphics.Color = AppTheme.colors.accent,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = valueColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
