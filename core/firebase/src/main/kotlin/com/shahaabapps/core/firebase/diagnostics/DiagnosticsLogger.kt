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
) : com.shahaabapps.core.common.Diag.Sink {

    override fun log(level: com.shahaabapps.core.common.Diag.Level, tag: String, message: String, t: Throwable?) =
        log(DiagLevel.valueOf(level.name), tag, message, t)

    @Volatile private var started = false

    /**
     * Called once from Application.onCreate: becomes the app-wide [Diag] sink and
     * records the app start, sign-in changes, screens, and foreground/background.
     */
    fun start(app: android.app.Application) {
        if (started) return
        started = true
        com.shahaabapps.core.common.Diag.sink = this
        info("app", "start ${context.packageName} $appVersion on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk ${android.os.Build.VERSION.SDK_INT}")
        var lastUid: String? = uid()
        auth.addAuthStateListener { a ->
            val now = a.currentUser?.uid
            if (now != lastUid) {
                val anon = a.currentUser?.isAnonymous == true
                info("auth", if (now == null) "signed out (was ${lastUid ?: "-"})" else "signed in uid=$now${if (anon) " (child)" else ""}")
                lastUid = now
            }
        }
        app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(a: android.app.Activity) {
                if (resumed++ == 0) info("app", "foreground")
                info("screen", a.javaClass.simpleName)
            }
            override fun onActivityPaused(a: android.app.Activity) {
                if (--resumed == 0) info("app", "background")
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        // Crashes: record before the default handler kills the process.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { log(DiagLevel.ERROR, "crash", "${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)} at ${e.stackTrace.firstOrNull()}", e) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Same tag+message within this window is counted, not uploaded again. */
    private val lastSeen = HashMap<String, Long>()
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
        val repeat = synchronized(lock) {
            val key = "$level|$tag|$message"
            val prev = lastSeen[key]
            lastSeen[key] = event.atMillis
            if (lastSeen.size > 500) lastSeen.clear()
            prev != null && event.atMillis - prev < REPEAT_WINDOW_MS
        }
        synchronized(lock) {
            ring.addLast(event)
            while (ring.size > RING_CAP) ring.removeFirst()
        }
        val crashlytics = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
        crashlytics?.log("[$level] $tag: $message")
        if (t != null) crashlytics?.recordException(t)
        else if (level == DiagLevel.ERROR) crashlytics?.recordException(DiagnosticException("$tag: $message"))
        if (!repeat) mirror(event, t)
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
        const val UPLOADS_PER_HOUR = 300
        const val REPEAT_WINDOW_MS = 60_000L
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Marker exception for logging a non-fatal diagnostic without a real throwable. */
class DiagnosticException(message: String) : Exception(message)
