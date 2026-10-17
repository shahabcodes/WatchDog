package com.shahaabapps.core.model

/** What the child is asking their parent for. */
enum class RequestKind { MORE_TIME, UNLOCK_APP, EXTEND_BEDTIME, USE_IN_SCHOOL }

/** Where the request stands. */
enum class RequestStatus { PENDING, APPROVED, DENIED }

/**
 * A child's "ask" to their parent (e.g. more screen time, or to unlock an app).
 * The child (Shield) creates it; a linked parent (Watch Dog) approves or denies.
 *
 * Firestore: `children/{childId}/requests/{id}`.
 */
data class TimeRequest(
    val id: String = "",
    val kind: RequestKind = RequestKind.MORE_TIME,
    /** For UNLOCK_APP / USE_IN_SCHOOL: which app. Blank otherwise. */
    val appPackage: String = "",
    val appLabel: String = "",
    /** Extra minutes requested (MORE_TIME / EXTEND_BEDTIME). */
    val minutes: Int = 0,
    val status: RequestStatus = RequestStatus.PENDING,
    val createdAtMillis: Long = 0L,
    val respondedAtMillis: Long = 0L,
    /**
     * When the parent's approval expires (absolute wall-clock millis). Set by the
     * parent when approving to control how long the app stays available; 0 means
     * "use the default window for this kind".
     */
    val grantUntilMillis: Long = 0L,
) {
    /** A short child-facing summary of the ask. */
    val summary: String
        get() = when (kind) {
            RequestKind.MORE_TIME ->
                if (appLabel.isNotBlank()) "$minutes more minutes of $appLabel" else "$minutes more minutes"
            RequestKind.UNLOCK_APP -> "to unlock ${appLabel.ifBlank { "an app" }}"
            RequestKind.EXTEND_BEDTIME -> "to stay up $minutes more minutes"
            RequestKind.USE_IN_SCHOOL -> "to use ${appLabel.ifBlank { "an app" }} during school"
        }
}
