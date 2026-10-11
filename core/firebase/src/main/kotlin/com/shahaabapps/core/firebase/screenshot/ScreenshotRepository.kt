package com.shahaabapps.core.firebase.screenshot

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.Screenshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * On-demand screen capture of the child device.
 *
 * Firestore:
 *  - `children/{childId}/commands/screenshot` = { nonce } — parent bumps the nonce
 *    to request a capture; the child listens and captures on a new nonce.
 *  - `children/{childId}/screenshots/{id}` = { url, capturedAtMillis } — results.
 * Storage: `screenshots/{childId}/{millis}.png`.
 */
interface ScreenshotRepository {
    /** Parent: ask the child device to take a screenshot now. */
    suspend fun requestCapture(childId: String): AppResult<Unit>

    /** Parent: observe pending (not-yet-downloaded) screenshots, newest first. */
    fun observeScreenshots(childId: String): Flow<List<Screenshot>>

    /**
     * Parent: download a screenshot's bytes, then delete the Storage object and its
     * Firestore doc so nothing is kept server-side.
     */
    suspend fun fetchAndClear(childId: String, shot: Screenshot): AppResult<ByteArray>

    /** Child: emits the latest request nonce (0 when none). */
    fun observeCaptureRequests(childId: String): Flow<Long>

    /** Child: upload a captured PNG and record it for the parent. */
    suspend fun uploadScreenshot(childId: String, pngBytes: ByteArray): AppResult<Unit>

    /** Child: record the last capture status/error for remote diagnosis. */
    suspend fun reportStatus(childId: String, status: String)

    /** Parent: delete one screenshot (doc only; storage object is lifecycle-managed). */
    suspend fun delete(childId: String, id: String): AppResult<Unit>
}

class FirebaseScreenshotRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ScreenshotRepository {

    override suspend fun requestCapture(childId: String): AppResult<Unit> = runCatchingResult {
        commandDoc(childId).set(mapOf("nonce" to System.currentTimeMillis())).await()
        Unit
    }

    override fun observeScreenshots(childId: String): Flow<List<Screenshot>> = callbackFlow {
        val registration = screenshotsCollection(childId)
            .orderBy("capturedAtMillis", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                trySend(
                    snapshot.documents.map { doc ->
                        Screenshot(
                            id = doc.id,
                            url = doc.getString("url") ?: "",
                            capturedAtMillis = doc.getLong("capturedAtMillis") ?: 0L,
                            storagePath = doc.getString("storagePath") ?: "",
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    override fun observeCaptureRequests(childId: String): Flow<Long> = callbackFlow {
        val registration = commandDoc(childId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(0L); return@addSnapshotListener
            }
            trySend(snapshot?.getLong("nonce") ?: 0L)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun uploadScreenshot(childId: String, pngBytes: ByteArray): AppResult<Unit> =
        runCatchingResult {
            val now = System.currentTimeMillis()
            val ref = storage.reference.child("screenshots/$childId/$now.png")
            ref.putBytes(pngBytes).await()
            val url = ref.downloadUrl.await().toString()
            screenshotsCollection(childId).add(
                mapOf(
                    "url" to url,
                    "storagePath" to ref.path,
                    "capturedAtMillis" to now,
                ),
            ).await()
            Unit
        }

    override suspend fun fetchAndClear(childId: String, shot: Screenshot): AppResult<ByteArray> =
        runCatchingResult {
            val ref = if (shot.storagePath.isNotBlank()) {
                storage.reference.child(shot.storagePath.trimStart('/'))
            } else {
                storage.reference.child("screenshots/$childId/${shot.capturedAtMillis}.png")
            }
            val bytes = ref.getBytes(MAX_SCREENSHOT_BYTES).await()
            // Clear the backend copy now that the parent has it locally.
            runCatching { ref.delete().await() }
            runCatching { screenshotsCollection(childId).document(shot.id).delete().await() }
            bytes
        }

    override suspend fun reportStatus(childId: String, status: String) {
        runCatching {
            firestore.collection("children").document(childId)
                .collection("commands").document("screenshotStatus")
                .set(mapOf("status" to status, "atMillis" to System.currentTimeMillis())).await()
        }
    }

    override suspend fun delete(childId: String, id: String): AppResult<Unit> = runCatchingResult {
        screenshotsCollection(childId).document(id).delete().await()
        Unit
    }

    private companion object {
        const val MAX_SCREENSHOT_BYTES = 12L * 1024 * 1024
    }

    private fun commandDoc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("commands").document("screenshot")

    private fun screenshotsCollection(childId: String) =
        firestore.collection("children").document(childId).collection("screenshots")
}
