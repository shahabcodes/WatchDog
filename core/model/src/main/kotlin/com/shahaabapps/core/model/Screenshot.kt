package com.shahaabapps.core.model

/**
 * A screenshot captured on the child device and uploaded for the parent to view.
 * The backend copy is transient: once the parent downloads it locally, the parent
 * deletes the Storage object + Firestore doc (nothing is kept server-side).
 */
data class Screenshot(
    val id: String = "",
    val url: String = "",
    val capturedAtMillis: Long = 0L,
    val storagePath: String = "",
    /**
     * Base64 JPEG bytes carried inline in the Firestore doc. When present the
     * parent decodes it directly (no Storage round-trip) — the fast path for the
     * small, downscaled captures. Empty when the image was large enough to fall
     * back to Cloud Storage ([url]/[storagePath]).
     */
    val data: String = "",
)
