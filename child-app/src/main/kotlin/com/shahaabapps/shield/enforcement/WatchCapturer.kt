package com.shahaabapps.shield.enforcement

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import com.shahaabapps.core.common.Diag
import com.shahaabapps.core.firebase.watch.WatchShotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/**
 * "Supervised" apps (AppRule.watchEverySeconds): while one is in front and the screen
 * is on and unlocked, takes one screenshot at a random moment in each interval the
 * parent chose, and sends it sealed to the parents. The child is told with a short
 * notice when a watched app opens. An app that hides its screen (a secure window,
 * e.g. banking) is reported once per visit instead of sending a black image.
 */
internal class WatchCapturer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: WatchShotRepository,
    private val childId: () -> String?,
    /** The app in front (last window change), or null. */
    private val front: () -> String?,
    /** Whether [pkg] is really the app on screen right now. */
    private val isShowing: (pkg: String) -> Boolean,
    /** Seconds between shots for [pkg]; 0 when it isn't watched or is blocked now. */
    private val intervalFor: (pkg: String) -> Int,
    private val labelFor: (pkg: String) -> String,
    /** Takes a screenshot: JPEG bytes, or null with hidden = true for a secure window. */
    private val grab: (onResult: (bytes: ByteArray?, hidden: Boolean) -> Unit) -> Unit,
) {
    private var job: Job? = null
    private val noticeAt = HashMap<String, Long>()

    fun start() {
        job?.cancel()
        job = scope.launch(Dispatchers.Main) { loop() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun CoroutineScope.loop() {
        var current: String? = null
        var slotStart = 0L
        var shotAt = 0L
        var hiddenSent = false
        while (isActive) {
            val pkg = front()?.takeIf { screenInUse() }
            val interval = pkg?.let { runCatching { intervalFor(it) }.getOrDefault(0) } ?: 0
            if (pkg == null || interval <= 0) {
                current = null
                delay(IDLE_TICK_MS)
                continue
            }
            val intervalMs = interval * 1000L
            val now = SystemClock.elapsedRealtime()
            if (pkg != current) {
                // A new visit: start the first interval now.
                current = pkg
                hiddenSent = false
                notify(pkg)
                slotStart = now
                shotAt = now + Random.nextLong(intervalMs)
            }
            if (now >= shotAt) {
                if (isShowing(pkg) && underDailyCap()) {
                    grab { bytes, hidden ->
                        if (bytes == null && (!hidden || hiddenSent)) return@grab
                        if (hidden) hiddenSent = true
                        send(pkg, bytes)
                    }
                }
                // The next interval: one random moment in it, never sooner than MIN_GAP.
                slotStart += intervalMs
                if (slotStart + intervalMs <= now) slotStart = now
                shotAt = maxOf(slotStart + Random.nextLong(intervalMs), now + MIN_GAP_MS)
            }
            delay((shotAt - now).coerceIn(200L, ACTIVE_TICK_MS))
        }
    }

    private fun send(pkg: String, bytes: ByteArray?) {
        val id = childId() ?: return
        val label = labelFor(pkg)
        val at = TrustedClock.now()
        scope.launch(Dispatchers.IO) {
            when (val r = repository.upload(id, pkg, label, bytes, at)) {
                is com.shahaabapps.core.common.AppResult.Success -> {
                    countShot()
                    Diag.info("watch", if (bytes == null) "app hid its screen" else "shot sent ${bytes.size}b")
                }
                is com.shahaabapps.core.common.AppResult.Failure ->
                    Diag.warn("watch", "shot not sent: ${r.error.message}")
            }
        }
    }

    /** Tells the child, at most every [NOTICE_EVERY_MS] per app. */
    private fun notify(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - (noticeAt[pkg] ?: -NOTICE_EVERY_MS) < NOTICE_EVERY_MS) return
        noticeAt[pkg] = now
        runCatching {
            Toast.makeText(
                context,
                "Your parent can see screenshots of ${labelFor(pkg)}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun screenInUse(): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return power?.isInteractive == true && keyguard?.isKeyguardLocked != true
    }

    private val prefs by lazy { context.getSharedPreferences("watch_shots", Context.MODE_PRIVATE) }

    private fun underDailyCap(): Boolean =
        prefs.getString("day", "") != LocalDate.now().toString() || prefs.getInt("count", 0) < DAILY_CAP

    private fun countShot() {
        val today = LocalDate.now().toString()
        val count = if (prefs.getString("day", "") == today) prefs.getInt("count", 0) else 0
        prefs.edit().putString("day", today).putInt("count", count + 1).apply()
    }

    private companion object {
        const val IDLE_TICK_MS = 2_000L
        const val ACTIVE_TICK_MS = 2_000L
        const val MIN_GAP_MS = 5_000L
        const val NOTICE_EVERY_MS = 10 * 60_000L
        /** Safety cap per child phone per day (cost and data). */
        const val DAILY_CAP = 500
    }
}
