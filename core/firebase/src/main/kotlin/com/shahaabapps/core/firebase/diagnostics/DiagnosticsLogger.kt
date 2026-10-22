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
 * - Every event is also uploaded to `diagEvents` (write-only for apps, kept 30
 *   days) with the date/time, app, version and phone, so problems on a phone far
 *   away can be read back for debugging (tools/diagnostics/read-diagnostics.js).
 *   Capped per hour so a loop can't flood it.
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
        mirror(event, t)
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

    private val appVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    private var windowStart = 0L
    private var windowCount = 0

    /** Uploads one event, at most [UPLOADS_PER_HOUR] per hour from this install. */
    private fun mirror(event: Event, t: Throwable?) {
        synchronized(lock) {
            if (event.atMillis - windowStart > 60 * 60 * 1000L) { windowStart = event.atMillis; windowCount = 0 }
            if (windowCount >= UPLOADS_PER_HOUR) return
            windowCount++
        }
        scope.launch {
            runCatching {
                firestore.collection(COLLECTION).add(
                    mapOf(
                        "at" to FieldValue.serverTimestamp(),
                        "atMillis" to event.atMillis,
                        "expireAt" to com.google.firebase.Timestamp(java.util.Date(event.atMillis + KEEP_MS)),
                        "level" to event.level.name,
                        "tag" to event.tag.take(40),
                        "message" to event.message.take(500),
                        "exception" to (t?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" } ?: "").take(300),
                        "uid" to (uid() ?: ""),
                        "install" to installId,
                        "app" to context.packageName,
                        "version" to appVersion,
                        "model" to (android.os.Build.MODEL ?: "").take(60),
                        "sdk" to android.os.Build.VERSION.SDK_INT,
                    ),
                )
            }
        }
    }

    private companion object {
        const val RING_CAP = 300
        const val COLLECTION = "diagEvents"
        const val UPLOADS_PER_HOUR = 120
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Marker exception for logging a non-fatal diagnostic without a real throwable. */
class DiagnosticException(message: String) : Exception(message)
