package com.shahaabapps.core.firebase.auth

/** Result of an email/password sign-in: either done, or a TOTP second factor is needed. */
sealed interface SignInOutcome {
    data class Success(val uid: String) : SignInOutcome
    /** The account has TOTP MFA enrolled; call [AuthRepository.resolveMfa] with the 6-digit code. */
    data object MfaRequired : SignInOutcome
}

/**
 * What the user needs to add the account to an authenticator app during TOTP
 * enrollment: the [otpauthUrl] to render as a QR, and [sharedKey] for manual entry.
 */
data class TotpEnrollment(
    val otpauthUrl: String,
    val sharedKey: String,
)
