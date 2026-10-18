package com.shahaabapps.watchdog

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.watchdog.feature.auth.AuthFlow
import com.shahaabapps.watchdog.feature.profile.ProfileSetupScreen
import com.shahaabapps.watchdog.messaging.WatchdogMessagingService
import com.shahaabapps.watchdog.ui.ParentAppState
import com.shahaabapps.watchdog.ui.ParentAppViewModel
import com.shahaabapps.watchdog.ui.RequestDeepLink
import com.shahaabapps.watchdog.ui.ThemeViewModel
import com.shahaabapps.watchdog.ui.shell.ParentShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // A pending request deep-link parsed from a notification tap / action button.
    // Read inside setContent (snapshot state) and cleared once handled.
    private val pendingDeepLink = mutableStateOf<RequestDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDeepLink.value = parseDeepLink(intent)
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
                        ParentAppRoot(
                            deepLink = pendingDeepLink.value,
                            onDeepLinkHandled = { pendingDeepLink.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)?.let { pendingDeepLink.value = it }
    }

    private fun parseDeepLink(intent: Intent?): RequestDeepLink? {
        intent ?: return null
        val childId = intent.getStringExtra(WatchdogMessagingService.EXTRA_CHILD_ID) ?: return null
        val requestId = intent.getStringExtra(WatchdogMessagingService.EXTRA_REQUEST_ID) ?: return null
        val action = intent.getStringExtra(WatchdogMessagingService.EXTRA_ACTION)
            ?: WatchdogMessagingService.ACTION_VIEW
        // Acted on — clear the status-bar notification. Action buttons (unlike a
        // content tap with setAutoCancel) never dismiss it on their own.
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(requestId.hashCode())
        }
        // Don't re-fire this deep-link if the activity is recreated (rotation,
        // process restart) — consume the extras from the intent.
        intent.removeExtra(WatchdogMessagingService.EXTRA_CHILD_ID)
        intent.removeExtra(WatchdogMessagingService.EXTRA_REQUEST_ID)
        intent.removeExtra(WatchdogMessagingService.EXTRA_ACTION)
        return RequestDeepLink(childId = childId, requestId = requestId, action = action)
    }
}

@Composable
private fun ParentAppRoot(
    deepLink: RequestDeepLink?,
    onDeepLinkHandled: () -> Unit,
    viewModel: ParentAppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        ParentAppState.Loading -> LoadingScreen()
        ParentAppState.SignedOut -> AuthFlow()
        is ParentAppState.NeedsProfile -> ProfileSetupScreen(onSaved = { /* state reacts automatically */ })
        is ParentAppState.Ready -> {
            androidx.compose.runtime.LaunchedEffect(current.profile.id) {
                viewModel.registerFcmToken(current.profile.id)
            }
            ParentShell(
                profile = current.profile,
                deepLink = deepLink,
                onDeepLinkHandled = onDeepLinkHandled,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        com.shahaabapps.core.ui.components.AppLoader(size = 46.dp)
    }
}
