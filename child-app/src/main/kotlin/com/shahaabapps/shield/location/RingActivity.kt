package com.shahaabapps.shield.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.shield.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen alarm shown when the parent rings the device. Behaves like a
 * standard alarm: it appears over the lock screen and the ring is silenced by
 * the on-screen buttons, the volume keys, or the power button (screen off).
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {

    private var message: String = ""

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty()

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val accentPalette by themeViewModel.accentPalette.collectAsStateWithLifecycle()
            AppTheme(themeMode = themeMode, accentPalette = accentPalette) {
                RingScreen(
                    message = message,
                    onStop = { dismiss() },
                    onSnooze = { snooze() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
    }

    // Volume keys silence the ring (like a normal alarm).
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            -> { dismiss(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun dismiss() {
        sendServiceAction(GeofenceSyncService.ACTION_RING_DISMISS)
        finish()
    }

    private fun snooze() {
        sendServiceAction(GeofenceSyncService.ACTION_RING_SNOOZE)
        finish()
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, GeofenceSyncService::class.java).setAction(action)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    companion object {
        const val EXTRA_MESSAGE = "ring_message"

        fun intent(context: Context, message: String): Intent =
            Intent(context, RingActivity::class.java)
                .putExtra(EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun RingScreen(message: String, onStop: () -> Unit, onSnooze: () -> Unit) {
    val gradient = Brush.verticalGradient(AppTheme.palette.gradient)
    val accent = AppTheme.colors.accent
    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
            Text(
                text = "Your family is\ntrying to reach you",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp),
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Text(
                text = "Press a volume or power button to stop",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = accent,
                ),
            ) { Text("Stop", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Snooze 5 min", fontSize = 16.sp) }
        }
    }
}
