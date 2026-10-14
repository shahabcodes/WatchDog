package com.shahaabapps.shield.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A captured location breadcrumb buffered on-device. Both the enforcement service
 * and the WorkManager backstop write points here first (always succeeds, even
 * offline/when the process is short-lived); a network-constrained flush worker
 * later uploads them to Firestore and deletes the rows on success.
 */
@Entity(tableName = "pending_trail")
data class PendingTrailPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: String,
    val lat: Double,
    val lng: Double,
    val timestampMillis: Long,
)
