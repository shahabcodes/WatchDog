package com.shahaabapps.core.model

/**
 * The child tried to open a blocked app. Recorded by Shield (at most one per app
 * per 10 minutes; [count] covers the attempts folded into this one) and sealed,
 * since which apps a child uses is end-to-end encrypted.
 */
data class BlockAttempt(
    val id: String = "",
    val packageName: String = "",
    val label: String = "",
    val reason: BlockAttemptReason = BlockAttemptReason.BLOCKED,
    val count: Int = 1,
    val atMillis: Long = 0L,
)

/** Why the app was blocked (stored by name inside the sealed event). */
enum class BlockAttemptReason {
    /** The parent blocked the app. */
    BLOCKED,
    /** Its daily limit is used up. */
    LIMIT,
    /** Outside the app's allowed hours. */
    HOURS,
    BEDTIME,
    SCHOOL,
    /** A new app waiting for the parent's review. */
    REVIEW,
    /** Installed from outside the Play Store (install guard). */
    INSTALL_GUARD,
    /** Opened in a pop-up / split-screen window while those are switched off. */
    MULTI_WINDOW,
    ;

    companion object {
        fun of(name: String?): BlockAttemptReason = entries.firstOrNull { it.name == name } ?: BLOCKED
    }
}
