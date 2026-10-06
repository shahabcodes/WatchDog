package com.shahaabapps.core.firebase.messaging

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Stores a parent's FCM registration tokens so the geofence Cloud Function can
 * push enter/exit alerts to their devices.
 *
 * Firestore layout: `users/{userId}/fcmTokens/{token}`.
 */
interface MessagingTokenRepository {
    suspend fun registerToken(userId: String, token: String): AppResult<Unit>
    suspend fun removeToken(userId: String, token: String): AppResult<Unit>
}

class FirebaseMessagingTokenRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MessagingTokenRepository {

    override suspend fun registerToken(userId: String, token: String): AppResult<Unit> =
        runCatchingResult {
            firestore.collection("users").document(userId)
                .collection("fcmTokens").document(token)
                .set(
                    mapOf(
                        "platform" to "android",
                        "updatedAtMillis" to System.currentTimeMillis(),
                    ),
                )
                .await()
            Unit
        }

    override suspend fun removeToken(userId: String, token: String): AppResult<Unit> =
        runCatchingResult {
            firestore.collection("users").document(userId)
                .collection("fcmTokens").document(token)
                .delete()
                .await()
            Unit
        }
}
