package com.shahaabapps.core.firebase.usage

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject

class FirebaseUsageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : UsageRepository {

    override suspend fun reportUsage(
        childId: String,
        date: String,
        usageMinutes: Map<String, Int>,
    ): AppResult<Unit> = runCatchingResult {
        // Per-app screen time reveals behaviour; E2E-encrypt the whole map (only the
        // parent/child apps read it). Plaintext fallback when the key isn't ready.
        val uid = authRepository.currentUserIdOrNull()
        val json = JSONObject().apply { usageMinutes.forEach { (k, v) -> put(k, v) } }.toString()
        val sealed = e2ee.sealString(childId, uid, json)
        val data: Map<String, Any> = if (sealed != null) {
            mapOf("e" to sealed, "enc" to true, "updatedAtMillis" to System.currentTimeMillis())
        } else {
            mapOf("apps" to usageMinutes, "updatedAtMillis" to System.currentTimeMillis())
        }
        usageDoc(childId, date).set(data).await()
    }

    override fun observeUsage(childId: String, date: String): Flow<Map<String, Int>> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = usageDoc(childId, date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyMap()); return@addSnapshotListener
            }
            trySend(snapshot?.decodeUsage(childId, viewerUid) ?: emptyMap())
        }
        awaitClose { registration.remove() }
    }

    override fun observeDailyTotals(childId: String, sinceDate: String): Flow<Map<String, Int>> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val query = firestore.collection("children").document(childId).collection("usage")
            .orderBy(FieldPath.documentId())
            .startAt(sinceDate)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyMap()); return@addSnapshotListener
            }
            val totals = snapshot.documents.associate { doc ->
                doc.id to doc.decodeUsage(childId, viewerUid).values.sum()
            }
            trySend(totals)
        }
        awaitClose { registration.remove() }
    }

    /** Per-app minutes for a usage doc, decrypting when sealed, else reading plaintext. */
    private fun DocumentSnapshot.decodeUsage(childId: String, viewerUid: String?): Map<String, Int> {
        val sealed = getString("e")
        if (getBoolean("enc") == true && sealed != null) {
            val json = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONObject(it) } ?: return emptyMap()
            return json.keys().asSequence().associateWith { json.optInt(it) }
        }
        @Suppress("UNCHECKED_CAST")
        return (get("apps") as? Map<String, Any?>).orEmpty().mapValues { (it.value as? Number)?.toInt() ?: 0 }
    }

    private fun usageDoc(childId: String, date: String) =
        firestore.collection("children").document(childId).collection("usage").document(date)
}
