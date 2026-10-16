package com.shahaabapps.core.firebase.installguard

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.InstallGuard
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Parent policy for sideloaded installs/updates.
 *
 * Firestore layout: `children/{childId}/settings/installGuard` { enabled,
 * allowlist, packages }. The parent writes it from Watch Dog; the child (Shield)
 * reads it to decide which downloaded APKs to quarantine.
 */
interface InstallGuardRepository {
    fun observe(childId: String): Flow<InstallGuard>
    suspend fun setGuard(childId: String, guard: InstallGuard): AppResult<Unit>
}

class FirebaseInstallGuardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : InstallGuardRepository {

    override fun observe(childId: String): Flow<InstallGuard> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(InstallGuard())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val pkgs = (snapshot.get("packages") as? List<String>) ?: emptyList()
            trySend(
                InstallGuard(
                    enabled = snapshot.getBoolean("enabled") ?: false,
                    allowlist = snapshot.getBoolean("allowlist") ?: false,
                    packages = pkgs,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setGuard(childId: String, guard: InstallGuard): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(
                mapOf(
                    "enabled" to guard.enabled,
                    "allowlist" to guard.allowlist,
                    "packages" to guard.packages,
                    "updatedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("installGuard")
}
