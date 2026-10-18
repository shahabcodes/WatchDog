package com.shahaabapps.core.firebase.screenshot

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.Screenshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    /** Parent: observe the child's last reported capture status (null when none). */
    fun observeStatus(childId: String): Flow<ScreenshotStatus?>

    /** Child: upload a captured JPEG and record it for the parent. */
    suspend fun uploadScreenshot(childId: String, imageBytes: ByteArray): AppResult<Unit>

    /** Child: record the last capture status/error for remote diagnosis. */
    suspend fun reportStatus(childId: String, status: String)

    /** Parent: delete one screenshot (doc only; storage object is lifecycle-managed). */
    suspend fun delete(childId: String, id: String): AppResult<Unit>
}

/** The child's last-reported capture status, for parent-side diagnosis. */
data class ScreenshotStatus(
    val status: String = "",
    val atMillis: Long = 0L,
)

class FirebaseScreenshotRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
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
                            data = doc.getString("data") ?: "",
                            enc = doc.getBoolean("enc") ?: false,
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

    override fun observeStatus(childId: String): Flow<ScreenshotStatus?> = callbackFlow {
        val registration = statusDoc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null); return@addSnapshotListener
            }
            trySend(
                ScreenshotStatus(
                    status = snapshot.getString("status") ?: "",
                    atMillis = snapshot.getLong("atMillis") ?: 0L,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun uploadScreenshot(childId: String, imageBytes: ByteArray): AppResult<Unit> =
        runCatchingResult {
            val now = System.currentTimeMillis()
            // Encrypt end-to-end once key exchange with every linked parent is
            // complete; until then upload plaintext so the feature keeps working
            // (the parent reads both). The data key is local, so this never needs
            // the network.
            val sealed = e2ee.sealBytesIfReady(childId, imageBytes)
            val payload = sealed ?: imageBytes
            val enc = sealed != null
            // Inline threshold is measured on the stored payload so a sealed blob
            // still stays under Firestore's 1 MiB document limit.
            if (payload.size <= INLINE_MAX_BYTES) {
                // Fast path: carry the bytes inline in the Firestore doc, so the
                // parent's realtime listener delivers them directly — no Storage
                // upload, no downloadUrl round-trip, no parent download.
                val base64 = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
                screenshotsCollection(childId).add(
                    mapOf(
                        "data" to base64,
                        "capturedAtMillis" to now,
                        "enc" to enc,
                    ),
                ).await()
            } else {
                // Fallback for unusually large captures: Cloud Storage. The content
                // type stays image/jpeg to satisfy the Storage rules even though a
                // sealed blob is ciphertext.
                val ref = storage.reference.child("screenshots/$childId/$now.jpg")
                val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
                ref.putBytes(payload, metadata).await()
                val url = ref.downloadUrl.await().toString()
                screenshotsCollection(childId).add(
                    mapOf(
                        "url" to url,
                        "storagePath" to ref.path,
                        "capturedAtMillis" to now,
                        "enc" to enc,
                    ),
                ).await()
            }
            Unit
        }

    override suspend fun fetchAndClear(childId: String, shot: Screenshot): AppResult<ByteArray> =
        runCatchingResult {
            val raw = when {
                // Fast path: bytes are inline in the doc — no network at all.
                shot.data.isNotBlank() ->
                    android.util.Base64.decode(shot.data, android.util.Base64.NO_WRAP)
                // Download via the stored download URL (token-authenticated,
                // independent of Storage read rules) — falling back to the SDK by path.
                shot.url.isNotBlank() -> withContext(Dispatchers.IO) {
                    java.net.URL(shot.url).openStream().use { it.readBytes() }
                }
                else -> withContext(Dispatchers.IO) {
                    val ref = storage.reference.child(
                        shot.storagePath.ifBlank { "screenshots/$childId/${shot.capturedAtMillis}.png" }
                            .trimStart('/'),
                    )
                    ref.getBytes(MAX_SCREENSHOT_BYTES).await()
                }
            }
            val bytes = if (shot.enc) {
                val parentUid = authRepository.currentUserIdOrNull()
                    ?: error("Signed out; can't decrypt this screenshot.")
                // On failure, propagate (don't delete below) so it can be retried
                // once this device's wrapped key is available.
                e2ee.openBytes(childId, parentUid, raw)
                    ?: error("Encryption key not ready yet — try again shortly.")
            } else {
                raw
            }
            // Delete the Firestore doc only after a successful decode; a Cloud
            // Function removes the Storage object on that deletion, so nothing is
            // kept server-side.
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
        // Inline up to ~600 KB of JPEG; base64 (~800 KB) stays well under Firestore's
        // 1 MiB document limit. Larger captures fall back to Cloud Storage.
        const val INLINE_MAX_BYTES = 600 * 1024
    }

    private fun commandDoc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("commands").document("screenshot")

    private fun statusDoc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("commands").document("screenshotStatus")

    private fun screenshotsCollection(childId: String) =
        firestore.collection("children").document(childId).collection("screenshots")
}
