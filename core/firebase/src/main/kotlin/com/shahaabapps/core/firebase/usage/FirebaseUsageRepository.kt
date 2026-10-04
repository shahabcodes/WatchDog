package com.shahaabapps.core.firebase.usage

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseUsageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UsageRepository {

    override suspend fun reportUsage(
        childId: String,
        date: String,
        usageMinutes: Map<String, Int>,
    ): AppResult<Unit> = runCatchingResult {
        usageDoc(childId, date).set(
            mapOf(
                "apps" to usageMinutes,
                "updatedAtMillis" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override fun observeUsage(childId: String, date: String): Flow<Map<String, Int>> = callbackFlow {
        val registration = usageDoc(childId, date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyMap()); return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val apps = (snapshot?.get("apps") as? Map<String, Any?>).orEmpty()
                .mapValues { (it.value as? Number)?.toInt() ?: 0 }
            trySend(apps)
        }
        awaitClose { registration.remove() }
    }

    private fun usageDoc(childId: String, date: String) =
        firestore.collection("children").document(childId).collection("usage").document(date)
}
