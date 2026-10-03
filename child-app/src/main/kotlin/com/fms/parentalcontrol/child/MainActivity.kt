package com.fms.parentalcontrol.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fms.parentalcontrol.child.feature.pairing.PairingScreen
import com.fms.parentalcontrol.child.feature.profile.ProfileSetupScreen
import com.fms.parentalcontrol.child.feature.status.ProtectedScreen
import com.fms.parentalcontrol.child.ui.ChildAppState
import com.fms.parentalcontrol.child.ui.ChildAppViewModel
import com.fms.parentalcontrol.child.ui.ErrorScreen
import com.fms.parentalcontrol.child.ui.LoadingScreen
import com.fms.parentalcontrol.child.ui.ThemeViewModel
import com.fms.parentalcontrol.core.ui.theme.AppTheme
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
                ChildAppRoot()
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
