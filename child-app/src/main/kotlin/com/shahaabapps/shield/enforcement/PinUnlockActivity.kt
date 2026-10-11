package com.shahaabapps.shield.enforcement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.PIN_MAX_LEN
import com.shahaabapps.core.model.PIN_MIN_LEN
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint

/** Asks for the parent's removal PIN; on success, allows Shield removal for a short window. */
@AndroidEntryPoint
class PinUnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PinUnlockScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun PinUnlockScreen(
    onDone: () -> Unit,
    viewModel: ProtectionStatusViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(48.dp))
        Text("Enter parent PIN", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Enter the removal PIN to let Shield be uninstalled for a couple of minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        CupertinoTextField(
            value = pin,
            onValueChange = { if (it.length <= PIN_MAX_LEN && it.all { c -> c.isDigit() }) { pin = it; error = false } },
            placeholder = "$PIN_MIN_LEN–$PIN_MAX_LEN digit PIN",
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) {
            Text("Incorrect PIN", color = AppTheme.colors.destructive, style = MaterialTheme.typography.bodySmall)
        }
        CupertinoPrimaryButton(
            text = "Allow removal",
            enabled = pin.length in PIN_MIN_LEN..PIN_MAX_LEN,
            onClick = {
                if (settings.pin.isNotEmpty() && pin == settings.pin) {
                    ProtectionLocalStore.unlock(context)
                    DeviceAdmin.remove(context)
                    onDone()
                } else {
                    error = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        CupertinoTextButton(text = "Cancel", onClick = onDone)
    }
}
