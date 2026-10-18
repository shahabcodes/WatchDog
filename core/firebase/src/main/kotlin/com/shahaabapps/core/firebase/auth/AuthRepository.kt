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

    /**
     * Signs in with email/password. If the account has TOTP MFA enrolled, returns
     * [SignInOutcome.MfaRequired] (the pending resolver is held internally) — finish
     * with [resolveMfa]. Otherwise [SignInOutcome.Success].
     */
    suspend fun signInWithEmail(email: String, password: String): AppResult<SignInOutcome>

    /** Completes an MFA-gated sign-in with the 6-digit TOTP [code]. */
    suspend fun resolveMfa(code: String): AppResult<String>

    suspend fun signInAnonymously(): AppResult<String>

    // --- TOTP multi-factor (parent accounts, opt-in) ---

    /** True when the signed-in user has a second factor enrolled. */
    fun isMfaEnrolled(): Boolean

    /** True when the signed-in user's email is verified (required before MFA enrollment). */
    fun isEmailVerified(): Boolean

    /** Sends a verification link to the signed-in user's email. */
    suspend fun sendEmailVerification(): AppResult<Unit>

    /** Reloads the user so a just-verified email is reflected in [isEmailVerified]. */
    suspend fun reloadUser(): AppResult<Unit>

    /** Re-authenticates the current user with their password (for recent-login-required ops). */
    suspend fun reauthenticate(password: String): AppResult<Unit>

    /** Begins TOTP enrollment: returns the QR/shared-key to add to an authenticator app. */
    suspend fun beginTotpEnrollment(): AppResult<TotpEnrollment>

    /** Finalizes the enrollment started by [beginTotpEnrollment] using the current 6-digit [code]. */
    suspend fun confirmTotpEnrollment(code: String): AppResult<Unit>

    /** Removes all enrolled second factors (turns MFA off). */
    suspend fun disableMfa(): AppResult<Unit>

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
