package com.fms.parentalcontrol.core.firebase.profile

import com.fms.parentalcontrol.core.common.AppResult
import com.fms.parentalcontrol.core.common.runCatchingResult
import com.fms.parentalcontrol.core.model.UserProfile
import com.fms.parentalcontrol.core.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firestore layout: `users/{userId}` holds the profile document.
 * Storage layout: `avatars/{userId}.jpg` holds the avatar image.
 */
class FirebaseProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ProfileRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toUserProfile(userId))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> =
        runCatchingResult {
            firestore.collection(COLLECTION_USERS)
                .document(profile.id)
                .set(profile.toMap())
                .await()
            Unit
        }

    override suspend fun uploadAvatar(userId: String, bytes: ByteArray): AppResult<String> =
        runCatchingResult {
            val ref = storage.reference.child("avatars/$userId.jpg")
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        }

    private fun UserProfile.toMap(): Map<String, Any?> = mapOf(
        FIELD_NAME to name,
        FIELD_PHOTO_URL to photoUrl,
        FIELD_DOB to dateOfBirthMillis,
        FIELD_ROLE to role.name,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(
        userId: String,
    ): UserProfile = UserProfile(
        id = userId,
        name = getString(FIELD_NAME).orEmpty(),
        photoUrl = getString(FIELD_PHOTO_URL),
        dateOfBirthMillis = getLong(FIELD_DOB),
        role = getString(FIELD_ROLE)?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            ?: UserRole.CHILD,
    )

    private companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_NAME = "name"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_DOB = "dateOfBirthMillis"
        const val FIELD_ROLE = "role"
    }
}
