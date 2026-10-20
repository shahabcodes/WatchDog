package com.shahaabapps.watchdog.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private const val CLEAR_AFTER_MS = 60_000L

/**
 * Copies a secret (backup codes, a 2FA key) to the clipboard marked sensitive, so
 * Android hides it in the clipboard preview and keyboards don't keep it in their
 * history, and clears it again after a minute if it's still there.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            else putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val current = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (current == text) {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, CLEAR_AFTER_MS)
}

/**
 * While shown, keeps this screen out of screenshots, screen recordings and the
 * Recents thumbnail (for the 2FA QR code and backup codes).
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
