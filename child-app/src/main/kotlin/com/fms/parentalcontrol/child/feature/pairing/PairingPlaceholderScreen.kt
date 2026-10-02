package com.fms.parentalcontrol.child.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.fms.parentalcontrol.core.model.UserProfile
import com.fms.parentalcontrol.core.ui.components.CupertinoScaffold
import com.fms.parentalcontrol.core.ui.theme.AppTheme
import com.fms.parentalcontrol.core.ui.theme.Spacing

/**
 * Placeholder shown once the child profile exists. In step 3 this becomes the
 * QR screen the parent scans to link accounts.
 */
@Composable
fun PairingPlaceholderScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    CupertinoScaffold(title = "Hi, ${profile.name}", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        ) {
            Text(
                text = "Ask a parent to scan your code",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Your pairing QR code will appear here (step 3).",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}
