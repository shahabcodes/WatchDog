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
    /**
     * True when [data] (or the Storage object) holds an E2EE-encrypted payload
     * (AES-256-GCM under the child's data key) rather than a raw JPEG. The parent
     * decrypts with its unwrapped copy of that key. Legacy plaintext captures
     * have this false, so the parent reads both during rollout.
     */
    val enc: Boolean = false,
)
