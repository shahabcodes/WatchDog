package com.shahaabapps.watchdog.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.core.ui.components.Avatar
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoDisclosure
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import androidx.compose.ui.graphics.Color
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    profile: UserProfile,
    onChildClick: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val children by viewModel.children.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.onQrScanned(result.contents)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contents = withContext(Dispatchers.IO) {
                    QrImageDecoder.decodeFromUri(context, uri)
                }
                if (contents != null) viewModel.onQrScanned(contents) else viewModel.onImageDecodeFailed()
            }
        }
    }

    fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the child's pairing code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    fun pickFromGallery() {
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        Hero(
            title = "Hi, ${profile.name}",
            subtitle = "Your family at a glance",
            actions = {
                HeroIconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (val state = linkState) {
                is LinkState.Error -> Banner(text = state.message, color = AppTheme.colors.destructive)
                is LinkState.Linking -> Banner(text = "Linking…", color = AppTheme.colors.accent)
                is LinkState.Success -> Banner(text = "Device linked!", color = AppTheme.colors.success)
                LinkState.Idle -> Unit
            }

            if (children.isEmpty()) {
                EmptyState()
            } else {
                Text(
                    text = "Children",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                children.forEach { child -> ChildRow(child, onClick = { onChildClick(child.childId) }) }
            }

            CupertinoPrimaryButton(
                text = "Scan child's QR",
                onClick = { launchScanner() },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            CupertinoTextButton(
                text = "Choose QR from gallery",
                onClick = { pickFromGallery() },
            )
        }
    }
}

@Composable
private fun ChildRow(child: ChildAccount, onClick: () -> Unit) {
    CupertinoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = child.profile.name, photoUrl = child.profile.photoUrl, size = 48.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = child.profile.name.ifBlank { "Child" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                StatusChip(
                    text = if (child.device.online) "Online" else "Offline",
                    color = if (child.device.online) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
                )
            }
            CupertinoDisclosure()
        }
    }
}

@Composable
private fun EmptyState() {
    CupertinoCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(
                text = "No children yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Open Shield on your child's phone and scan the code it shows.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    CupertinoCard {
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
