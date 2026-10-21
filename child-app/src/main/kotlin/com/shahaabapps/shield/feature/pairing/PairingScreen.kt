package com.shahaabapps.shield.feature.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.shahaabapps.core.ui.components.AppLoader
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.shield.ui.ShieldMark

private val CREAM = Color(0xFFFFF6E0)
private val NAVY = Color(0xFF00133F)
private val NAVY2 = Color(0xFF012169)
private val MUTED = Color(0xFF5C4A1E)

@Composable
fun PairingScreen(
    child: ChildAccount,
    modifier: Modifier = Modifier,
    keysViewModel: PairingKeysViewModel = hiltViewModel(),
) {
    val publicKey by keysViewModel.publicKeyB64.collectAsStateWithLifecycle()
    val code by keysViewModel.code.collectAsStateWithLifecycle()
    val codeError by keysViewModel.error.collectAsStateWithLifecycle()
    val restoring by keysViewModel.restoring.collectAsStateWithLifecycle()
    if (restoring) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text("Restoring…") },
            text = { Text("Your parent is restoring this phone. Shield will restart in a moment.") },
            confirmButton = { },
        )
    }
    val qrBitmap = remember(child.childId, code, publicKey) {
        code?.let { QrCodeGenerator.generate(PairingCodec.encode(PairingData(child.childId, it, publicKey))) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CREAM)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShieldMark(size = 40.dp)
            Text("Shield", color = NAVY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Hi ${child.profile.name.ifBlank { "there" }}!",
            color = NAVY,
            fontFamily = AppFonts.lighthouse,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.7).sp,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = "This device isn't supervised yet. Ask a parent to open Watch Dog and scan this code to connect.",
            color = MUTED,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        // Navy viewport holding a white QR tile (stays dark-on-white so it scans).
        Box(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .size(260.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(NAVY),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qrBitmap != null) {
                    Image(bitmap = qrBitmap, contentDescription = "Pairing QR code", modifier = Modifier.size(176.dp))
                } else {
                    Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                        AppLoader(size = 28.dp, color = MUTED, strokeWidth = 3.dp)
                    }
                }
            }
        }

        codeError?.let { Text(it, color = Color(0xFFC1362B), fontSize = 14.sp, textAlign = TextAlign.Center) }
        Text("Or enter this code · it changes every 10 minutes", color = MUTED, fontSize = 13.sp)
        Text(
            text = code?.let { "${it.take(4)} ${it.drop(4)}" } ?: "…",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = 6.sp,
            color = NAVY2,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0DDB0))
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            AppLoader(size = 16.dp, color = MUTED, strokeWidth = 2.dp)
            Text("Waiting for a parent to connect…", color = MUTED, fontSize = 14.sp)
        }
    }
}
