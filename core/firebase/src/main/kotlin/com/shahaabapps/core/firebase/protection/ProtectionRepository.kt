package com.shahaabapps.core.firebase.protection

import com.shahaabapps.core.firebase.callSafely

import com.shahaabapps.core.common.AppResult
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
    /**
     * For enforcement on the child: emits only server-confirmed settings (the doc
     * exists and the snapshot isn't from cache), never on an error or a missing
     * doc — so a glitch can't switch protection off or drop device admin.
     */
    fun observeConfirmed(childId: String): Flow<ProtectionSettings>

    suspend fun save(
        childId: String,
        uninstallBlocked: Boolean,
        blockNewApps: Boolean,
        lockSettings: Boolean,
        newPin: String?,
    ): AppResult<Unit>

    /** Parent: moves an old readable PIN hash to the server-only store. */
    suspend fun migratePin(childId: String): AppResult<Unit>

    /**
     * Child: checks a typed removal PIN on the server (rate limited). Returns the
     * unlock deadline in server time. Needs a network connection.
     */
    suspend fun verifyRemovalPin(pin: String): AppResult<Long>

    /** Which alerts parents get for this child (all on by default). */
    fun observeAlerts(childId: String): Flow<com.shahaabapps.core.model.AlertSettings>

    suspend fun saveAlerts(childId: String, alerts: com.shahaabapps.core.model.AlertSettings): AppResult<Unit>
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
                    lockSettings = snapshot?.getBoolean("lockSettings")
                        ?: (snapshot?.getBoolean("uninstallBlocked") ?: false),
                    pinSet = snapshot?.getBoolean("hasPin") ?: false,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override fun observeConfirmed(childId: String): Flow<ProtectionSettings> = callbackFlow {
        val registration = doc(childId).addSnapshotListener(
            com.google.firebase.firestore.MetadataChanges.INCLUDE,
        ) { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists() || snapshot.metadata.isFromCache) {
                return@addSnapshotListener
            }
            trySend(
                ProtectionSettings(
                    uninstallBlocked = snapshot.getBoolean("uninstallBlocked") ?: false,
                    pinSalt = snapshot.getString("pinSalt") ?: "",
                    pinHash = snapshot.getString("pinHash") ?: "",
                    legacyPin = snapshot.getString("pin") ?: "",
                    blockNewApps = snapshot.getBoolean("blockNewApps") ?: false,
                    lockSettings = snapshot.getBoolean("lockSettings")
                        ?: (snapshot.getBoolean("uninstallBlocked") ?: false),
                    pinSet = snapshot.getBoolean("hasPin") ?: false,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(
        childId: String,
        uninstallBlocked: Boolean,
        blockNewApps: Boolean,
        lockSettings: Boolean,
        newPin: String?,
    ): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("protection.save", "childId=$childId")
            val base = mutableMapOf<String, Any>(
                "uninstallBlocked" to uninstallBlocked,
                "blockNewApps" to blockNewApps,
                "lockSettings" to lockSettings,
                "updatedAtMillis" to System.currentTimeMillis(),
            )
            // merge: the PIN fields are server-managed and left untouched here.
            doc(childId).set(base, SetOptions.merge()).await()
            // The PIN is hashed and stored server-side, out of the child's reach.
            if (newPin != null) {
                functions.getHttpsCallable("setRemovalPin")
                    .callSafely(name = "setRemovalPin", data = hashMapOf("childId" to childId, "pin" to newPin))
                    
            }
            Unit
        }

    override suspend fun migratePin(childId: String): AppResult<Unit> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("protection.migratePin", "childId=$childId")
        functions.getHttpsCallable("migratePin").callSafely(name = "migratePin", data = hashMapOf("childId" to childId))
        Unit
    }

    override suspend fun verifyRemovalPin(pin: String): AppResult<Long> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("protection.verifyRemovalPin")
        val data = functions.getHttpsCallable("verifyRemovalPin")
            .callSafely(name = "verifyRemovalPin", data = hashMapOf("pin" to pin)).getData() as? Map<*, *>
        (data?.get("unlockUntilMillis") as? Number)?.toLong() ?: error("No unlock returned")
    }

    private val functions get() = com.google.firebase.functions.FirebaseFunctions.getInstance()

    override fun observeAlerts(childId: String): Flow<com.shahaabapps.core.model.AlertSettings> = callbackFlow {
        val registration = alertsDoc(childId).addSnapshotListener { snapshot, _ ->
            trySend(
                com.shahaabapps.core.model.AlertSettings(
                    protectionOff = snapshot?.getBoolean("protectionOff") ?: true,
                    clockChanged = snapshot?.getBoolean("clockChanged") ?: true,
                    fakeGps = snapshot?.getBoolean("fakeGps") ?: true,
                    travel = snapshot?.getBoolean("travel") ?: true,
                    noCheckIn = snapshot?.getBoolean("noCheckIn") ?: true,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveAlerts(childId: String, alerts: com.shahaabapps.core.model.AlertSettings): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("protection.saveAlerts", "childId=$childId")
            alertsDoc(childId).set(
                mapOf(
                    "protectionOff" to alerts.protectionOff,
                    "clockChanged" to alerts.clockChanged,
                    "fakeGps" to alerts.fakeGps,
                    "travel" to alerts.travel,
                    "noCheckIn" to alerts.noCheckIn,
                    "updatedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
            Unit
        }

    private fun alertsDoc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("alerts")

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("protection")
}
