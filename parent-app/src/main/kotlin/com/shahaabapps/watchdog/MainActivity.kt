package com.shahaabapps.watchdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.watchdog.feature.auth.AuthScreen
import com.shahaabapps.watchdog.feature.dashboard.DashboardScreen
import com.shahaabapps.watchdog.feature.profile.ProfileSetupScreen
import com.shahaabapps.watchdog.ui.ParentAppState
import com.shahaabapps.watchdog.ui.ParentAppViewModel
import com.shahaabapps.watchdog.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            AppTheme(themeMode = themeMode) {
                ParentAppRoot()
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
        is ParentAppState.Ready -> DashboardScreen(profile = current.profile)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
