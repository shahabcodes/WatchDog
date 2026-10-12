package com.shahaabapps.shield.feature.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.PairingCodec
import com.shahaabapps.core.model.PairingData
import com.shahaabapps.core.ui.components.Avatar
import com.shahaabapps.core.ui.components.CupertinoScaffold
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun PairingScreen(
    child: ChildAccount,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(child.childId, child.pairingCode) {
        QrCodeGenerator.generate(
            PairingCodec.encode(PairingData(child.childId, child.pairingCode)),
        )
    }

    CupertinoScaffold(title = "Link a Parent", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Avatar(name = child.profile.name, photoUrl = child.profile.photoUrl, size = 64.dp)
            Text(
                text = "Hi ${child.profile.name}!",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "This device isn't supervised — no restrictions are active. " +
                    "Ask a parent to open Watch Dog and scan this code to connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )

            // QR in a white rounded card so it scans well in dark mode too.
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Pairing QR code",
                    modifier = Modifier.size(240.dp),
                )
            }

            // Fallback manual code.
            Text(
                text = "Or enter code",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
            Text(
                text = child.pairingCode,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 6.sp,
                color = AppTheme.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            WaitingRow()
        }
    }
}

@Composable
private fun WaitingRow() {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    ) {
        com.shahaabapps.core.ui.components.AppLoader(
            size = 16.dp,
            color = AppTheme.colors.secondaryLabel,
            strokeWidth = 2.dp,
        )
        Text(
            text = "Waiting for a parent to connect…",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
        )
    }
}
