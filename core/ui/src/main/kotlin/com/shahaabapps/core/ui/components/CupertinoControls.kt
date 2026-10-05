package com.shahaabapps.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Shapes

/** An iOS-green toggle. */
@Composable
fun CupertinoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AppTheme.colors.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE9E9EA),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** A filled, full-width iOS-style primary button with a subtle brand gradient. */
@Composable
fun CupertinoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = Shapes.button
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.background(com.shahaabapps.core.ui.theme.Brand.accentGradient)
                } else {
                    Modifier.background(AppTheme.colors.accent.copy(alpha = 0.4f))
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** A borderless iOS-style text button (e.g. "Cancel", "Skip"). */
@Composable
fun CupertinoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val color = if (destructive) AppTheme.colors.destructive else AppTheme.colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = color,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 12.dp),
        )
    }
}
