package com.shahaabapps.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shahaabapps.core.ui.theme.AppTheme

/**
 * Branded centred dialog matching [ConfirmDialog]: a rounded themed card with an
 * optional accent icon badge, a bold title, an arbitrary [content] slot (for
 * body text, fields, a QR, etc.), and a split action row. Use this instead of a
 * raw Material [androidx.compose.material3.AlertDialog] so dialogs match the app.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = "Cancel",
    dismissOnOutsideTouch: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = { if (dismissOnOutsideTouch) onDismiss() },
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AppTheme.colors.card,
            shadowElevation = 16.dp,
            modifier = modifier.width(320.dp),
        ) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(28.dp))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = if (icon != null) 14.dp else 0.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 20.dp),
                    content = content,
                )
                if (confirmText != null || dismissText != null) {
                    HorizontalDivider(color = AppTheme.colors.separator)
                    Row(modifier = Modifier.height(52.dp).fillMaxWidth()) {
                        if (dismissText != null) {
                            DialogAction(dismissText, AppTheme.colors.secondaryLabel, false, true, onDismiss, Modifier.weight(1f))
                        }
                        if (dismissText != null && confirmText != null) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(AppTheme.colors.separator))
                        }
                        if (confirmText != null) {
                            DialogAction(
                                confirmText,
                                if (confirmEnabled) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                                true,
                                confirmEnabled,
                                { if (confirmEnabled) onConfirm?.invoke() },
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: Color,
    bold: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight().clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, fontSize = 16.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
