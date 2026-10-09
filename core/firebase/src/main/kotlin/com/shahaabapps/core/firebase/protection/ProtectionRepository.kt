package com.shahaabapps.core.firebase.protection

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.ProtectionSettings
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Anti-tamper (uninstall) protection settings.
 *
 * Firestore layout: `children/{childId}/settings/protection`. The parent writes
 * it from Watch Dog; the child (Shield) reads it to enforce protection.
 */
interface ProtectionRepository {
    fun observe(childId: String): Flow<ProtectionSettings>
    suspend fun save(childId: String, settings: ProtectionSettings): AppResult<Unit>
}

class FirebaseProtectionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ProtectionRepository {

    override fun observe(childId: String): Flow<ProtectionSettings> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ProtectionSettings())
                return@addSnapshotListener
            }
            trySend(
                ProtectionSettings(
                    uninstallBlocked = snapshot?.getBoolean("uninstallBlocked") ?: false,
                    pin = snapshot?.getString("pin") ?: "",
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(childId: String, settings: ProtectionSettings): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(
                mapOf(
                    "uninstallBlocked" to settings.uninstallBlocked,
                    "pin" to settings.pin,
                    "updatedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("protection")
}
