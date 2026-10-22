package com.shahaabapps.core.common

/**
 * App-wide diagnostics entry point, usable from anywhere (receivers, services,
 * extension functions) without injection. The app's diagnostics logger installs
 * itself as [sink] at start; before that, calls are dropped.
 *
 * PRIVACY: tags, ids, flags, outcomes and error types only. Never locations,
 * decrypted content, typed text or PINs.
 */
object Diag {
    interface Sink {
        fun log(level: Level, tag: String, message: String, t: Throwable?)
    }

    enum class Level { INFO, WARN, ERROR }

    @Volatile var sink: Sink? = null

    fun info(tag: String, message: String) = sink?.log(Level.INFO, tag, message, null)
    fun warn(tag: String, message: String, t: Throwable? = null) = sink?.log(Level.WARN, tag, message, t)
    fun error(tag: String, message: String, t: Throwable? = null) = sink?.log(Level.ERROR, tag, message, t)

    /** A user or app action, e.g. action("ring", "child=…"). */
    fun action(name: String, detail: String = "") = info("action", if (detail.isEmpty()) name else "$name $detail")

    /**
     * A failed operation caught by [runCatchingResult]: where it happened (first
     * app frame) and what. Connection failures are expected offline, so WARN.
     */
    fun failure(t: Throwable) {
        val where = t.stackTrace.firstOrNull { it.className.startsWith("com.shahaabapps") }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "?"
        val offline = generateSequence(t) { it.cause }.any { it is java.io.IOException } ||
            t.javaClass.simpleName.contains("Offline")
        val msg = "$where ${t.javaClass.simpleName}: ${t.message.orEmpty().take(200)}"
        if (offline) warn("fail", msg) else error("fail", msg, t)
    }
}
