package com.shahaabapps.core.firebase.protection

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.crypto.PinHasher
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.ProtectionSettings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Anti-tamper (uninstall) protection settings.
 *
 * Firestore layout: `children/{childId}/settings/protection`. The parent writes
 * it from Watch Dog; the child (Shield) reads it to enforce protection. The
 * removal PIN is stored only as a salted hash — never in plaintext.
 */
interface ProtectionRepository {
    fun observe(childId: String): Flow<ProtectionSettings>

    /**
     * Writes protection settings. [newPin] is the plaintext PIN to (re)set; it is
     * hashed before storage and never persisted in clear. Pass null to keep the
     * existing stored PIN unchanged (e.g. when only toggling a switch).
     */
    suspend fun save(
        childId: String,
        uninstallBlocked: Boolean,
        blockNewApps: Boolean,
        newPin: String?,
    ): AppResult<Unit>
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
                    pinSalt = snapshot?.getString("pinSalt") ?: "",
                    pinHash = snapshot?.getString("pinHash") ?: "",
                    legacyPin = snapshot?.getString("pin") ?: "",
                    blockNewApps = snapshot?.getBoolean("blockNewApps") ?: false,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(
        childId: String,
        uninstallBlocked: Boolean,
        blockNewApps: Boolean,
        newPin: String?,
    ): AppResult<Unit> =
        runCatchingResult {
            val base = mutableMapOf<String, Any>(
                "uninstallBlocked" to uninstallBlocked,
                "blockNewApps" to blockNewApps,
                "updatedAtMillis" to System.currentTimeMillis(),
            )
            if (newPin != null) {
                // Set (or replace) the PIN: store only a salted hash, and purge any
                // legacy plaintext field from older documents.
                val (salt, hash) = PinHasher.hash(newPin)
                base["pinSalt"] = salt
                base["pinHash"] = hash
                base["pin"] = FieldValue.delete()
            }
            // merge so a null newPin leaves the existing pinSalt/pinHash untouched.
            doc(childId).set(base, SetOptions.merge()).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("protection")
}
