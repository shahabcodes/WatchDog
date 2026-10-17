package com.shahaabapps.core.firebase.installguard

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.InstallGuard
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import javax.inject.Inject

/**
 * Parent policy for sideloaded installs/updates.
 *
 * Firestore layout: `children/{childId}/settings/installGuard` { enabled,
 * allowlist, packages }. The parent writes it from Watch Dog; the child (Shield)
 * reads it to decide which downloaded APKs to quarantine. The package list is
 * E2E-encrypted (only the parent/child key holders read it); the on/off + mode
 * flags stay plaintext.
 */
interface InstallGuardRepository {
    fun observe(childId: String): Flow<InstallGuard>
    suspend fun setGuard(childId: String, guard: InstallGuard): AppResult<Unit>
}

class FirebaseInstallGuardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : InstallGuardRepository {

    override fun observe(childId: String): Flow<InstallGuard> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(InstallGuard())
                return@addSnapshotListener
            }
            trySend(
                InstallGuard(
                    enabled = snapshot.getBoolean("enabled") ?: false,
                    allowlist = snapshot.getBoolean("allowlist") ?: false,
                    packages = snapshot.decodePackages(childId, viewerUid),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setGuard(childId: String, guard: InstallGuard): AppResult<Unit> =
        runCatchingResult {
            val uid = authRepository.currentUserIdOrNull()
            val sealed = e2ee.sealString(childId, uid, JSONArray(guard.packages).toString())
            val data: Map<String, Any> = buildMap {
                put("enabled", guard.enabled)
                put("allowlist", guard.allowlist)
                put("updatedAtMillis", System.currentTimeMillis())
                if (sealed != null) {
                    put("pkgsE", sealed)
                    put("enc", true)
                } else {
                    put("packages", guard.packages)
                }
            }
            doc(childId).set(data).await()
            Unit
        }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.decodePackages(childId: String, viewerUid: String?): List<String> {
        val sealed = getString("pkgsE")
        if (getBoolean("enc") == true && sealed != null) {
            val arr = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONArray(it) } ?: return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        }
        return (get("packages") as? List<String>) ?: emptyList()
    }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("installGuard")
}
