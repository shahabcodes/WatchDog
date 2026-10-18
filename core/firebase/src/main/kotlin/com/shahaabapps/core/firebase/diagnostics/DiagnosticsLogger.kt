package com.shahaabapps.core.firebase.diagnostics

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagLevel { INFO, WARN, ERROR }

/**
 * App-wide diagnostics: breadcrumbs, non-fatal exceptions and crashes, for
 * debugging bugs that aren't crashes (e.g. an E2EE decrypt returning null).
 *
 * - Everything is forwarded to Crashlytics (log breadcrumbs + recordException).
 * - A capped in-memory ring is kept for the in-app "export / share" screen.
 * - WARN/ERROR events are also mirrored to Firestore (metadata only) so there's a
 *   backend trail.
 *
 * PRIVACY: metadata only — never log decrypted content, locations, app names, or
 * any E2E-encrypted payload. Tags/ids/flags/error types only.
 *
 * On by default; [enabled] can be flipped off from settings later.
 */
@Singleton
class DiagnosticsLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun uid(): String? = auth.currentUser?.uid
    data class Event(val atMillis: Long, val level: DiagLevel, val tag: String, val message: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val ring = ArrayDeque<Event>()
    private fun prefs() = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    /** On by default; persisted so a user who turns it off stays off. */
    @Volatile var enabled: Boolean = runCatching { prefs().getBoolean("enabled", true) }.getOrDefault(true)
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
        runCatching { prefs().edit().putBoolean("enabled", value).apply() }
    }

    private val installId: String by lazy {
        prefs().getString("installId", null) ?: UUID.randomUUID().toString().also {
            prefs().edit().putString("installId", it).apply()
        }
    }

    fun info(tag: String, message: String) = log(DiagLevel.INFO, tag, message, null)
    fun warn(tag: String, message: String) = log(DiagLevel.WARN, tag, message, null)
    fun error(tag: String, message: String, t: Throwable? = null) = log(DiagLevel.ERROR, tag, message, t)

    fun log(level: DiagLevel, tag: String, message: String, t: Throwable? = null) {
        if (!enabled) return
        val event = Event(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            ring.addLast(event)
            while (ring.size > RING_CAP) ring.removeFirst()
        }
        val crashlytics = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
        crashlytics?.log("[$level] $tag: $message")
        if (t != null) crashlytics?.recordException(t)
        else if (level == DiagLevel.ERROR) crashlytics?.recordException(DiagnosticException("$tag: $message"))
        // Mirror only the important events to the backend to limit writes.
        if (level != DiagLevel.INFO) mirror(event, t)
    }

    /** A plain-text dump of recent events for the in-app export / share. */
    fun recentText(): String = synchronized(lock) {
        buildString {
            appendLine("Watch Dog diagnostics")
            appendLine("install: $installId  uid: ${uid() ?: "-"}")
            appendLine("events: ${ring.size}")
            appendLine("----")
            ring.forEach { appendLine("${it.atMillis} [${it.level}] ${it.tag}: ${it.message}") }
        }
    }

    fun recent(): List<Event> = synchronized(lock) { ring.toList() }

    private fun mirror(event: Event, t: Throwable?) {
        scope.launch {
            runCatching {
                firestore.collection("diagnostics").document(installId).collection("events").add(
                    mapOf(
                        "at" to FieldValue.serverTimestamp(),
                        "atMillis" to event.atMillis,
                        "level" to event.level.name,
                        "tag" to event.tag,
                        "message" to event.message,
                        "uid" to (uid() ?: ""),
                        "exception" to (t?.javaClass?.simpleName ?: ""),
                    ),
                )
            }
        }
    }

    private companion object {
        const val RING_CAP = 300
    }
}

/** Marker exception for logging a non-fatal diagnostic without a real throwable. */
class DiagnosticException(message: String) : Exception(message)
