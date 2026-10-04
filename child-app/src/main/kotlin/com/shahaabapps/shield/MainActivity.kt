package com.shahaabapps.shield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.shield.feature.pairing.PairingScreen
import com.shahaabapps.shield.feature.profile.ProfileSetupScreen
import com.shahaabapps.shield.feature.status.ProtectedScreen
import com.shahaabapps.shield.ui.ChildAppState
import com.shahaabapps.shield.ui.ChildAppViewModel
import com.shahaabapps.shield.ui.ErrorScreen
import com.shahaabapps.shield.ui.LoadingScreen
import com.shahaabapps.shield.ui.ThemeViewModel
import com.shahaabapps.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val accentPalette by themeViewModel.accentPalette.collectAsStateWithLifecycle()

            AppTheme(themeMode = themeMode, accentPalette = accentPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ChildAppRoot()
                }
            }
        }
    }
}

@Composable
private fun ChildAppRoot(
    viewModel: ChildAppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        ChildAppState.Loading -> LoadingScreen(Modifier)
        is ChildAppState.Error -> ErrorScreen(message = current.message, onRetry = viewModel::retry)
        is ChildAppState.NeedsProfile -> ProfileSetupScreen(onSaved = { /* state reacts automatically */ })
        is ChildAppState.Pairing -> PairingScreen(child = current.child)
        is ChildAppState.Protected -> ProtectedScreen(child = current.child)
    }
}
