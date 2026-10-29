package com.shahaabapps.shield.enforcement

import android.content.Context
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.Diag
import com.shahaabapps.core.firebase.blockattempt.BlockAttemptRepository
import com.shahaabapps.core.firebase.blockattempt.BlockAttemptThrottle
import com.shahaabapps.core.model.BlockAttempt
import com.shahaabapps.core.model.BlockAttemptReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Records the child's attempts to open a blocked app (full-screen block or a
 * pop-up / split / PiP cover) so the parents get an alert. Throttled on the phone
 * to one event per app per 10 minutes; the attempts in between are counted into
 * that app's next event. The event is sealed: the app's name never leaves the
 * phone in plaintext, and Diag never names it either.
 */
internal class BlockAttemptRecorder(
    context: Context,
    private val repository: BlockAttemptRepository,
    private val childId: () -> String?,
    private val scope: CoroutineScope,
) {
    // Survives service restarts, so a restart can't turn into a burst of alerts.
    private val prefs = context.getSharedPreferences("block_attempts", Context.MODE_PRIVATE)

    private val throttle = BlockAttemptThrottle(
        object : BlockAttemptThrottle.Store {
            override fun get(packageName: String): BlockAttemptThrottle.State? {
                val raw = prefs.getString(packageName, null) ?: return null
                val sent = raw.substringBefore(',').toLongOrNull() ?: return null
                val pending = raw.substringAfter(',', "").toIntOrNull() ?: 0
                return BlockAttemptThrottle.State(lastSentAt = sent, pending = pending)
            }

            override fun put(packageName: String, state: BlockAttemptThrottle.State) {
                prefs.edit().putString(packageName, "${state.lastSentAt},${state.pending}").apply()
            }
        },
    )

    fun record(packageName: String, label: String, reason: BlockAttemptReason) {
        val id = childId() ?: return
        val now = TrustedClock.now()
        val count = throttle.onAttempt(packageName, now) ?: return
        scope.launch {
            val attempt = BlockAttempt(packageName = packageName, label = label, reason = reason, count = count, atMillis = now)
            when (val r = repository.report(id, attempt)) {
                is AppResult.Success -> Diag.info("enforce", "block attempt recorded reason=${reason.name} count=$count")
                is AppResult.Failure -> {
                    throttle.onSendFailed(packageName, count)
                    Diag.warn("enforce", "block attempt not recorded: ${r.error.message}")
                }
            }
        }
    }
}
