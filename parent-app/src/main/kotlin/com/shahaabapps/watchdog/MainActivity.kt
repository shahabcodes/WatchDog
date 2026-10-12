package com.shahaabapps.watchdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.watchdog.feature.auth.AuthScreen
import com.shahaabapps.watchdog.feature.profile.ProfileSetupScreen
import com.shahaabapps.watchdog.ui.ParentAppState
import com.shahaabapps.watchdog.ui.ParentAppViewModel
import com.shahaabapps.watchdog.ui.ThemeViewModel
import com.shahaabapps.watchdog.ui.shell.ParentShell
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
                    com.shahaabapps.core.ui.components.CelebrationHost {
                        ParentAppRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentAppRoot(
    viewModel: ParentAppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        ParentAppState.Loading -> LoadingScreen()
        ParentAppState.SignedOut -> AuthScreen()
        is ParentAppState.NeedsProfile -> ProfileSetupScreen(onSaved = { /* state reacts automatically */ })
        is ParentAppState.Ready -> {
            androidx.compose.runtime.LaunchedEffect(current.profile.id) {
                viewModel.registerFcmToken(current.profile.id)
            }
            ParentShell(profile = current.profile)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        com.shahaabapps.core.ui.components.AppLoader(size = 46.dp)
    }
}
