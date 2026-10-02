package com.fms.parentalcontrol.parent.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fms.parentalcontrol.core.model.UserProfile
import com.fms.parentalcontrol.core.ui.components.CupertinoDisclosure
import com.fms.parentalcontrol.core.ui.components.CupertinoListRow
import com.fms.parentalcontrol.core.ui.components.CupertinoListSection
import com.fms.parentalcontrol.core.ui.components.CupertinoScaffold
import com.fms.parentalcontrol.core.ui.theme.Spacing

/**
 * Placeholder dashboard shown once a parent profile exists. The child switcher,
 * QR pairing, rules, geofences and reports arrive in later steps.
 */
@Composable
fun HomeScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    CupertinoScaffold(title = "Hi, ${profile.name}", modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        ) {
            CupertinoListSection(
                header = "Children",
                footer = "Pair a child device by scanning its QR code (coming in step 3).",
            ) {
                CupertinoListRow(
                    title = "Add a child",
                    leadingIcon = Icons.Default.Add,
                    showDivider = false,
                    onClick = { /* QR pairing — step 3 */ },
                    trailing = { CupertinoDisclosure() },
                )
            }

            Text(
                text = "More features coming soon",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}
