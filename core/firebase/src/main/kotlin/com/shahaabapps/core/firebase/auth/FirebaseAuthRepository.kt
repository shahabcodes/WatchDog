package com.shahaabapps.core.firebase.auth

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val messagingTokenRepository: MessagingTokenRepository,
) : AuthRepository {

    override val currentUserId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUserIdOrNull(): String? = auth.currentUser?.uid

    override fun currentUserEmail(): String? = auth.currentUser?.email

    override suspend fun signUpWithEmail(email: String, password: String): AppResult<String> =
        runCatchingResult {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            requireNotNull(result.user?.uid) { "Sign-up succeeded but uid was null" }
        }

    override suspend fun signInWithEmail(email: String, password: String): AppResult<String> =
        runCatchingResult {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            requireNotNull(result.user?.uid) { "Sign-in succeeded but uid was null" }
        }

    override suspend fun signInAnonymously(): AppResult<String> =
        runCatchingResult {
            val result = auth.signInAnonymously().await()
            requireNotNull(result.user?.uid) { "Anonymous sign-in returned null uid" }
        }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> =
        runCatchingResult {
            auth.sendPasswordResetEmail(email).await()
        }

    override suspend fun signOut() {
        // Detach this device's FCM token from the signed-in account BEFORE signing
        // out (afterwards the delete would be denied). Best-effort: a failure here
        // must not block sign-out.
        val uid = auth.currentUser?.uid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                messagingTokenRepository.removeToken(uid, token)
            }
        }
        auth.signOut()
    }
}
