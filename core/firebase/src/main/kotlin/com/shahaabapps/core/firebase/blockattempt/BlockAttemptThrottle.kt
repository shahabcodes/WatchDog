package com.shahaabapps.core.firebase.blockattempt

/**
 * Shield's on-device throttle for blocked-app alerts: at most one event per app
 * per [windowMillis]. Attempts in between are counted and folded into that app's
 * next event ("tried 4 times"), so the parent isn't pushed on every tap.
 */
class BlockAttemptThrottle(
    private val store: Store,
    private val windowMillis: Long = WINDOW_MS,
) {
    /** Per-app state; kept on the phone only (it names apps). */
    interface Store {
        fun get(packageName: String): State?
        fun put(packageName: String, state: State)
    }

    /** [pending]: attempts not yet reported since the event sent at [lastSentAt]. */
    data class State(val lastSentAt: Long, val pending: Int)

    /**
     * Records one attempt. Returns how many attempts the event to send now should
     * report, or null when this one is held for the next event.
     */
    @Synchronized
    fun onAttempt(packageName: String, now: Long): Int? {
        val state = store.get(packageName) ?: State(lastSentAt = 0L, pending = 0)
        val attempts = state.pending + 1
        // A clock moved back past the last event doesn't hold attempts forever.
        val held = now >= state.lastSentAt && now - state.lastSentAt < windowMillis
        if (held) {
            store.put(packageName, state.copy(pending = attempts))
            return null
        }
        store.put(packageName, State(lastSentAt = now, pending = 0))
        return attempts
    }

    /** The event for [count] attempts wasn't written: carry them, and send on the next attempt. */
    @Synchronized
    fun onSendFailed(packageName: String, count: Int) {
        val state = store.get(packageName) ?: State(lastSentAt = 0L, pending = 0)
        store.put(packageName, State(lastSentAt = 0L, pending = state.pending + count))
    }

    companion object {
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}
