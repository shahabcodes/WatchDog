package com.shahaabapps.core.firebase.auth

import com.shahaabapps.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Authentication boundary used by both apps. The child app typically uses
 * anonymous sign-in, the parent app email/password.
 */
interface AuthRepository {

    /** Emits the current signed-in user id, or null when signed out. */
    val currentUserId: Flow<String?>

    fun currentUserIdOrNull(): String?

    /** The signed-in user's email, or null (e.g. anonymous / signed out). */
    fun currentUserEmail(): String?

    suspend fun signUpWithEmail(email: String, password: String): AppResult<String>

    suspend fun signInWithEmail(email: String, password: String): AppResult<String>

    suspend fun signInAnonymously(): AppResult<String>

    /** Sends a password-reset email to [email]. */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    /**
     * Signs out. First removes this device's FCM token from the current user's
     * `fcmTokens` so the device stops receiving that account's push notifications
     * — otherwise, on a shared device, a later sign-in to a different account keeps
     * getting the previous account's child alerts. Must run while still signed in
     * (the delete needs auth), so this suspends until the token is cleared.
     */
    suspend fun signOut()
}
