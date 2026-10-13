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
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.theme.LighthouseType

// Lighthouse palette (fixed — the ring alarm is always on-brand, like the block screen).
private val NAVY_DEEP = Color(0xFF00133F)
private val NAVY = Color(0xFF0A2E80)
private val NAVY_BORDER = Color(0xFF3A5AA8)
private val GOLD = Color(0xFFF5B83D)
private val SUBTITLE = Color(0xFFC7D3F0)
private val GREEN = Color(0xFF1E8E5A)

/**
 * Full-screen alarm shown when the parent rings the device (Shield prototype
 * screen 5, "Parent is ringing"). Behaves like a standard alarm: it appears over
 * the lock screen and the ring is silenced by the on-screen button, the volume
 * keys, or the power button (screen off). Tapping "I'm okay" stops the alarm and
 * shows a confirmation; Done closes the screen.
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
            LighthouseType {
                RingScreen(
                    message = message,
                    onStopRinging = { stopRinging() },
                    onSnooze = { snooze() },
                    onDone = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
    }

    // Volume keys silence the ring (like a normal alarm) and close the screen.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            -> { dismiss(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Silence the alarm but keep the screen up so the child sees the confirmation. */
    private fun stopRinging() {
        sendServiceAction(GeofenceSyncService.ACTION_RING_DISMISS)
    }

    /** Silence the alarm and close the screen (hardware-button stop). */
    private fun dismiss() {
        stopRinging()
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
private fun RingScreen(
    message: String,
    onStopRinging: () -> Unit,
    onSnooze: () -> Unit,
    onDone: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NAVY_DEEP)
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("SHIELD", color = GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

        Beacon(ringing = !acknowledged)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (acknowledged) "They know you're okay" else "Your family is looking for you",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 32.sp,
                letterSpacing = (-0.7).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                when {
                    acknowledged -> "We stopped the alarm and let your family know you're okay."
                    message.isNotBlank() -> message
                    else -> "Your phone is ringing loudly, even on silent. Let them know you're okay."
                },
                color = SUBTITLE,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        if (acknowledged) {
            GoldButton(text = "Done", onClick = onDone)
        } else {
            GoldButton(
                text = "I'm okay, stop ringing",
                onClick = { acknowledged = true; onStopRinging() },
            )
            OutlineButton(text = "Snooze 5 minutes", icon = { tint ->
                Icon(Icons.Filled.Snooze, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }, onClick = onSnooze)
        }
    }
}

@Composable
private fun Beacon(ringing: Boolean) {
    val reduceMotion = remember {
        try {
            android.animation.ValueAnimator.getDurationScale() == 0f
        } catch (_: Throwable) {
            false
        }
    }
    val progress = if (ringing && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "ripple")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rippleProgress",
        ).value
    } else {
        0f
    }

    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        if (ringing && !reduceMotion) {
            Ripple(progress)
            Ripple((progress + 0.5f) % 1f)
        } else if (ringing) {
            // Reduced motion: a single still ring.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .border(3.dp, GOLD.copy(alpha = 0.25f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .background(NAVY)
                .border(4.dp, if (ringing) GOLD else GREEN, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ringing) Icons.Filled.NotificationsActive else Icons.Filled.Check,
                contentDescription = null,
                tint = if (ringing) GOLD else GREEN,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** One expanding, fading gold ring. [p] runs 0→1: scales 0.6→1.25 and fades 0.55→0. */
@Composable
private fun Ripple(p: Float) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(0.6f + p * 0.65f)
            .border(3.dp, GOLD.copy(alpha = (0.55f * (1f - p)).coerceAtLeast(0f)), CircleShape),
    )
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GOLD)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = NAVY_DEEP, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun OutlineButton(
    text: String,
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, NAVY_BORDER, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(Color.White)
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
