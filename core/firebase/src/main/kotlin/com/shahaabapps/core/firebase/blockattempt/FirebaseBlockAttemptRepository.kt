package com.shahaabapps.core.firebase.blockattempt

import com.google.firebase.firestore.FirebaseFirestore
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.BlockAttempt
import com.shahaabapps.core.model.BlockAttemptReason
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject

class FirebaseBlockAttemptRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : BlockAttemptRepository {

    override suspend fun report(childId: String, attempt: BlockAttempt): AppResult<Unit> =
        runCatchingResult {
            val doc = attempts(childId).document()
            val secret = JSONObject()
                .put("packageName", attempt.packageName)
                .put("label", attempt.label)
                .put("reason", attempt.reason.name)
                .put("count", attempt.count)
                .toString()
            // Unlike geofence events there is no plaintext fallback: the rules only
            // accept a sealed attempt, so without a key the attempt isn't sent.
            val sealed = e2ee.sealString(childId, authRepository.currentUserIdOrNull(), secret, slot = slot(doc.id))
                ?: error("encryption key not ready")
            doc.set(mapOf("enc" to true, "e" to sealed, "atMillis" to attempt.atMillis)).await()
            Unit
        }

    override suspend fun getEvent(childId: String, eventId: String): BlockAttempt? {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        return runCatching {
            val doc = attempts(childId).document(eventId).get().await()
            if (!doc.exists()) return@runCatching null
            val sealed = doc.getString("e") ?: return@runCatching null
            val json = e2ee.openStringCached(childId, viewerUid, sealed, slot = slot(eventId))
                ?.let { JSONObject(it) } ?: return@runCatching null
            BlockAttempt(
                id = eventId,
                packageName = json.optString("packageName"),
                label = json.optString("label"),
                reason = BlockAttemptReason.of(json.optString("reason")),
                count = json.optInt("count", 1).coerceAtLeast(1),
                atMillis = doc.getLong("atMillis") ?: 0L,
            )
        }.getOrNull()
    }

    override suspend fun deleteOlderThan(childId: String, cutoffMillis: Long): AppResult<Unit> =
        runCatchingResult {
            val snap = attempts(childId).whereLessThan("atMillis", cutoffMillis).limit(200).get().await()
            if (snap.isEmpty) return@runCatchingResult
            val batch = firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Unit
        }

    private fun attempts(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(ATTEMPTS)

    /** Per-document binding, so a sealed attempt can't be replayed under another id. */
    private fun slot(eventId: String) = "$ATTEMPTS/$eventId"

    private companion object {
        const val CHILDREN = "children"
        const val ATTEMPTS = "blockAttempts"
    }
}
