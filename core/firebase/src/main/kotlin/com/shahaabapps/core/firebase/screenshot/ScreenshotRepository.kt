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

    /** Parent: observe captured screenshots, newest first. */
    fun observeScreenshots(childId: String): Flow<List<Screenshot>>

    /** Child: emits the latest request nonce (0 when none). */
    fun observeCaptureRequests(childId: String): Flow<Long>

    /** Child: upload a captured PNG and record it for the parent. */
    suspend fun uploadScreenshot(childId: String, pngBytes: ByteArray): AppResult<Unit>

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

    override suspend fun delete(childId: String, id: String): AppResult<Unit> = runCatchingResult {
        screenshotsCollection(childId).document(id).delete().await()
        Unit
    }

    private fun commandDoc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("commands").document("screenshot")

    private fun screenshotsCollection(childId: String) =
        firestore.collection("children").document(childId).collection("screenshots")
}
