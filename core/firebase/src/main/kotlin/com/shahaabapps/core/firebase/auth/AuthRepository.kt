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

    suspend fun signUpWithEmail(email: String, password: String): AppResult<String>

    suspend fun signInWithEmail(email: String, password: String): AppResult<String>

    suspend fun signInAnonymously(): AppResult<String>

    fun signOut()
}
