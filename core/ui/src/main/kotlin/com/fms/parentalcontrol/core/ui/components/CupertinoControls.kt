package com.fms.parentalcontrol.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fms.parentalcontrol.core.ui.theme.AppTheme
import com.fms.parentalcontrol.core.ui.theme.Shapes

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
            checkedTrackColor = AppTheme.colors.success,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE9E9EA),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** A filled, full-width iOS-style primary button. */
@Composable
fun CupertinoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.accent,
            contentColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
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
