package com.shahaabapps.core.model

/**
 * A remote screenshot request/result exchanged via Firestore.
 * The image travels as a compressed base64 JPEG (kept under Firestore's 1 MB
 * document limit), avoiding a dependency on Cloud Storage.
 */
data class ScreenshotCommand(
    val id: String = "",
    val status: ScreenshotStatus = ScreenshotStatus.REQUESTED,
    val requestedAtMillis: Long = 0L,
    val completedAtMillis: Long = 0L,
    val imageBase64: String? = null,
    val error: String? = null,
)

enum class ScreenshotStatus { REQUESTED, CAPTURED, FAILED }
