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

    fun signOut()
}
