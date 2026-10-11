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
)
