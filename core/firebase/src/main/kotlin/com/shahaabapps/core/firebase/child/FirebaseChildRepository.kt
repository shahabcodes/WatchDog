package com.shahaabapps.core.firebase.child

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.DeviceInfo
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.core.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.random.Random

class FirebaseChildRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChildRepository {

    override fun observeChild(childId: String): Flow<ChildAccount?> = callbackFlow {
        val registration = childrenCollection.document(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toChildAccount())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun saveChildProfile(
        childId: String,
        profile: UserProfile,
    ): AppResult<Unit> = runCatchingResult {
        val doc = childrenCollection.document(childId)
        val existing = doc.get().await()
        val code = existing.getString(FIELD_PAIRING_CODE) ?: generatePairingCode()
        @Suppress("UNCHECKED_CAST")
        val linkedParents = existing.get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList()

        val data = mapOf(
            FIELD_PROFILE to mapOf(
                "name" to profile.name,
                "photoUrl" to profile.photoUrl,
                "dateOfBirthMillis" to profile.dateOfBirthMillis,
            ),
            FIELD_PAIRING_CODE to code,
            FIELD_PAIRING_ACTIVE to linkedParents.isEmpty(),
            FIELD_LINKED_PARENTS to linkedParents,
            FIELD_ROLE to UserRole.CHILD.name,
        )
        doc.set(data, SetOptions.merge()).await()
    }

    override fun observeLinkedChildren(parentId: String): Flow<List<ChildAccount>> = callbackFlow {
        val registration = childrenCollection
            .whereArrayContains(FIELD_LINKED_PARENTS, parentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toChildAccount() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun linkChild(
        childId: String,
        code: String,
        parentId: String,
    ): AppResult<Unit> = runCatchingResult {
        val doc = childrenCollection.document(childId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            require(snapshot.exists()) { "No child found for this code." }
            val active = snapshot.getBoolean(FIELD_PAIRING_ACTIVE) ?: false
            val storedCode = snapshot.getString(FIELD_PAIRING_CODE)
            require(active) { "This code is no longer active." }
            require(storedCode != null && storedCode.equals(code, ignoreCase = true)) {
                "The code doesn't match. Check and try again."
            }
            transaction.update(doc, FIELD_LINKED_PARENTS, FieldValue.arrayUnion(parentId))
        }.await()
        Unit
    }

    override suspend fun heartbeat(childId: String, model: String): AppResult<Unit> =
        runCatchingResult {
            childrenCollection.document(childId).set(
                mapOf(
                    FIELD_DEVICE to mapOf(
                        "online" to true,
                        "lastSeenMillis" to System.currentTimeMillis(),
                        "model" to model,
                    ),
                ),
                SetOptions.merge(),
            ).await()
        }

    private fun DocumentSnapshot.toChildAccount(): ChildAccount {
        @Suppress("UNCHECKED_CAST")
        val profileMap = get(FIELD_PROFILE) as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val linkedParents = get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val deviceMap = get(FIELD_DEVICE) as? Map<String, Any?> ?: emptyMap()

        return ChildAccount(
            childId = id,
            profile = UserProfile(
                id = id,
                name = profileMap["name"] as? String ?: "",
                photoUrl = profileMap["photoUrl"] as? String,
                dateOfBirthMillis = profileMap["dateOfBirthMillis"] as? Long,
                role = UserRole.CHILD,
            ),
            pairingCode = getString(FIELD_PAIRING_CODE) ?: "",
            linkedParents = linkedParents,
            device = DeviceInfo(
                online = deviceMap["online"] as? Boolean ?: false,
                batteryPercent = (deviceMap["batteryPercent"] as? Long)?.toInt() ?: 0,
                model = deviceMap["model"] as? String ?: "",
                lastSeenMillis = deviceMap["lastSeenMillis"] as? Long ?: 0L,
            ),
        )
    }

    private fun generatePairingCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous chars
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    private val childrenCollection get() = firestore.collection(COLLECTION_CHILDREN)

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val FIELD_PROFILE = "profile"
        const val FIELD_PAIRING_CODE = "pairingCode"
        const val FIELD_PAIRING_ACTIVE = "pairingActive"
        const val FIELD_LINKED_PARENTS = "linkedParents"
        const val FIELD_DEVICE = "device"
        const val FIELD_ROLE = "role"
    }
}
