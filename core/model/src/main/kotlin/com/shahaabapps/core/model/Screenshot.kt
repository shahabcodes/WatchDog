package com.shahaabapps.core.model

/** A screenshot captured on the child device and uploaded for the parent to view. */
data class Screenshot(
    val id: String = "",
    val url: String = "",
    val capturedAtMillis: Long = 0L,
)
