package com.shahaabapps.core.firebase.watch

import com.shahaabapps.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Screenshots of "Supervised" apps (see AppRule.watchEverySeconds).
 *
 * Firestore layout: children/{childId}/watchShots/{shotId} =
 * {enc: true, e: sealed {p, l, h}, data: sealed JPEG (base64, "" when the app hid
 * its screen), capturedAtMillis, expireAt}. Both the app and the image are sealed.
 * Unlike on-demand screenshots, nothing deletes a shot on download, so every
 * parent gets each one; a TTL policy on `expireAt` removes them after [KEEP_DAYS].
 */
interface WatchShotRepository {

    /**
     * Child: send one capture of [packageName] taken at [capturedAtMillis]. [image]
     * null records that the app hid its screen (a secure window). Fails when the
     * encryption key isn't ready (never written in plaintext).
     */
    suspend fun upload(
        childId: String,
        packageName: String,
        label: String,
        image: ByteArray?,
        capturedAtMillis: Long,
    ): AppResult<Unit>

    /** Parent: shots captured after [afterMillis] (child clock), live, oldest first. */
    fun observeAfter(childId: String, afterMillis: Long): Flow<List<WatchShotRecord>>

    /** Parent: decrypt one record; null when it can't be opened yet. */
    suspend fun open(childId: String, record: WatchShotRecord): WatchShot?

    companion object {
        const val KEEP_DAYS = 3
    }
}

/** A stored, still-sealed shot. */
data class WatchShotRecord(
    val id: String,
    val capturedAtMillis: Long,
    val sealedMeta: String,
    val sealedImage: String,
)

/** A decrypted shot; [image] null when the app hid its screen. */
class WatchShot(
    val packageName: String,
    val label: String,
    val capturedAtMillis: Long,
    val image: ByteArray?,
)
