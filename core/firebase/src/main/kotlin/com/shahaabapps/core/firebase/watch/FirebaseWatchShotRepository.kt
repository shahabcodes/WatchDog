package com.shahaabapps.core.firebase.watch

import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject

class FirebaseWatchShotRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : WatchShotRepository {

    override suspend fun upload(
        childId: String,
        packageName: String,
        label: String,
        image: ByteArray?,
        capturedAtMillis: Long,
    ): AppResult<Unit> = runCatchingResult {
        val doc = shots(childId).document()
        val meta = JSONObject()
            .put("p", packageName)
            .put("l", label)
            .put("h", image == null)
            .toString()
        // No plaintext fallback: the rules only accept sealed shots.
        val sealedMeta = e2ee.sealString(childId, authRepository.currentUserIdOrNull(), meta, slot = metaSlot(doc.id))
            ?: error("encryption key not ready")
        val sealedImage = image?.let {
            e2ee.sealBytesIfReady(childId, it, slot = imageSlot(doc.id)) ?: error("encryption key not ready")
        }
        val data = sealedImage?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty()
        require(data.length < MAX_DATA_CHARS) { "screenshot too large" }
        doc.set(
            mapOf(
                "enc" to true,
                "e" to sealedMeta,
                "data" to data,
                "capturedAtMillis" to capturedAtMillis,
                "expireAt" to Timestamp(Date(System.currentTimeMillis() + WatchShotRepository.KEEP_DAYS * DAY_MS)),
            ),
        ).await()
        Unit
    }

    override fun observeAfter(childId: String, afterMillis: Long): Flow<List<WatchShotRecord>> = callbackFlow {
        val registration = shots(childId)
            .whereGreaterThan("capturedAtMillis", afterMillis)
            .orderBy("capturedAtMillis", Query.Direction.ASCENDING)
            .limit(PAGE)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        WatchShotRecord(
                            id = doc.id,
                            capturedAtMillis = doc.getLong("capturedAtMillis") ?: return@mapNotNull null,
                            sealedMeta = doc.getString("e") ?: return@mapNotNull null,
                            sealedImage = doc.getString("data").orEmpty(),
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun open(childId: String, record: WatchShotRecord): WatchShot? {
        val viewerUid = authRepository.currentUserIdOrNull() ?: return null
        e2ee.warmReaderKey(childId, viewerUid)
        val meta = e2ee.openStringCached(childId, viewerUid, record.sealedMeta, slot = metaSlot(record.id))
            ?.let { JSONObject(it) } ?: return null
        val image = if (record.sealedImage.isBlank()) {
            null
        } else {
            val blob = runCatching { Base64.decode(record.sealedImage, Base64.NO_WRAP) }.getOrNull() ?: return null
            e2ee.openBytes(childId, viewerUid, blob, slot = imageSlot(record.id)) ?: return null
        }
        return WatchShot(
            packageName = meta.optString("p"),
            label = meta.optString("l"),
            capturedAtMillis = record.capturedAtMillis,
            image = image,
        )
    }

    private fun shots(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(SHOTS)

    // Per-document binding, so a sealed shot can't be replayed under another id.
    private fun metaSlot(id: String) = "$SHOTS/$id/meta"
    private fun imageSlot(id: String) = "$SHOTS/$id/image"

    private companion object {
        const val CHILDREN = "children"
        const val SHOTS = "watchShots"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val PAGE = 500L
        // Firestore docs are capped at 1 MiB; the rules allow 900k characters.
        const val MAX_DATA_CHARS = 880_000
    }
}
