package com.shahaabapps.core.firebase.auth

import com.shahaabapps.core.firebase.callSafely

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpSecret
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val messagingTokenRepository: MessagingTokenRepository,
    private val e2eeManager: E2eeManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : AuthRepository {

    // Pending state between a sign-in that needs MFA and its resolution, and between
    // beginning TOTP enrollment and confirming it. Singleton repo → single user.
    @Volatile private var pendingResolver: MultiFactorResolver? = null
    @Volatile private var pendingTotpSecret: TotpSecret? = null
    // Set by a backup-code sign-in (which turns 2FA off); read once by the app.
    @Volatile private var backupCodeSignInNotice = false

    private val functions get() = FirebaseFunctions.getInstance()

    override val currentUserId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUserIdOrNull(): String? = auth.currentUser?.uid

    override fun currentUserEmail(): String? = auth.currentUser?.email

    override suspend fun signUpWithEmail(email: String, password: String): AppResult<String> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.signUpWithEmail")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            requireNotNull(result.user?.uid) { "Sign-up succeeded but uid was null" }
        }

    override suspend fun signInWithEmail(email: String, password: String): AppResult<SignInOutcome> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.signInWithEmail")
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                SignInOutcome.Success(requireNotNull(result.user?.uid) { "Sign-in succeeded but uid was null" })
            } catch (e: FirebaseAuthMultiFactorException) {
                pendingResolver = e.resolver
                SignInOutcome.MfaRequired
            }
        }

    override suspend fun resolveMfa(code: String): AppResult<String> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.resolveMfa")
            val resolver = requireNotNull(pendingResolver) { "No MFA sign-in in progress" }
            val hint = resolver.hints.firstOrNull { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }
                ?: error("This account's second factor isn't a TOTP authenticator.")
            val assertion = TotpMultiFactorGenerator.getAssertionForSignIn(hint.uid, code.trim())
            val result = resolver.resolveSignIn(assertion).await()
            pendingResolver = null
            requireNotNull(result.user?.uid) { "MFA sign-in returned null uid" }
        }

    override suspend fun signInAnonymously(): AppResult<String> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.signInAnonymously")
            val result = auth.signInAnonymously().await()
            requireNotNull(result.user?.uid) { "Anonymous sign-in returned null uid" }
        }

    override suspend fun signInWithCustomToken(token: String): AppResult<String> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.signInWithCustomToken")
            val result = auth.signInWithCustomToken(token).await()
            requireNotNull(result.user?.uid) { "Sign-in returned null uid" }
        }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.sendPasswordReset")
            auth.sendPasswordResetEmail(email).await()
        }

    override suspend fun changePassword(newPassword: String): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.changePassword")
            requireNotNull(auth.currentUser) { "Not signed in" }.updatePassword(newPassword).await()
            Unit
        }

    override fun isMfaEnrolled(): Boolean =
        auth.currentUser?.multiFactor?.enrolledFactors?.isNotEmpty() == true

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    override suspend fun sendEmailVerification(): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.sendEmailVerification")
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.sendEmailVerification().await()
            Unit
        }

    override suspend fun reloadUser(): AppResult<Unit> =
        runCatchingResult { auth.currentUser?.reload()?.await(); Unit }

    override suspend fun reauthenticate(password: String): AppResult<SignInOutcome> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.reauthenticate")
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            val email = requireNotNull(user.email) { "No email on this account" }
            try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                SignInOutcome.Success(user.uid)
            } catch (e: FirebaseAuthMultiFactorException) {
                // 2FA on: the re-auth also needs the authenticator code (resolveMfa).
                pendingResolver = e.resolver
                SignInOutcome.MfaRequired
            }
        }

    override suspend fun beginTotpEnrollment(): AppResult<TotpEnrollment> =
        runCatchingResult {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            val session = user.multiFactor.session.await()
            val secret = TotpMultiFactorGenerator.generateSecret(session).await()
            pendingTotpSecret = secret
            val email = user.email ?: "Watch Dog"
            TotpEnrollment(
                otpauthUrl = secret.generateQrCodeUrl(email, "Watch Dog"),
                sharedKey = secret.sharedSecretKey,
            )
        }

    override suspend fun confirmTotpEnrollment(code: String): AppResult<Unit> =
        runCatchingResult {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            val secret = requireNotNull(pendingTotpSecret) { "Start enrollment first" }
            val assertion = TotpMultiFactorGenerator.getAssertionForEnrollment(secret, code.trim())
            user.multiFactor.enroll(assertion, "Authenticator app").await()
            pendingTotpSecret = null
            Unit
        }

    override suspend fun disableMfa(): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.disableMfa")
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.multiFactor.enrolledFactors.forEach { factor ->
                user.multiFactor.unenroll(factor).await()
            }
            Unit
        }

    override suspend fun generateBackupCodes(): AppResult<List<String>> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("auth.generateBackupCodes")
            val result = functions.getHttpsCallable("generateMfaBackupCodes").callSafely(name = "generateMfaBackupCodes")
            val codes = (result.getData() as? Map<*, *>)?.get("codes") as? List<*>
            requireNotNull(codes) { "No codes returned" }.map { it.toString() }
        }

    override suspend fun backupCodeStatus(): AppResult<BackupCodeStatus> =
        runCatchingResult {
            val result = functions.getHttpsCallable("getMfaBackupCodeStatus").callSafely(name = "getMfaBackupCodeStatus")
            val data = result.getData() as? Map<*, *> ?: emptyMap<Any, Any>()
            BackupCodeStatus(
                remaining = (data["remaining"] as? Number)?.toInt() ?: 0,
                total = (data["total"] as? Number)?.toInt() ?: 0,
            )
        }

    override suspend fun signInWithBackupCode(email: String, password: String, code: String): AppResult<String> =
        runCatchingResult {
            redeemBackupCode(email, password, code)
            // 2FA is now off server-side, so a plain password sign-in completes.
            pendingResolver = null
            val result = auth.signInWithEmailAndPassword(email, password).await()
            backupCodeSignInNotice = true
            requireNotNull(result.user?.uid) { "Sign-in succeeded but uid was null" }
        }

    override suspend fun disableMfaWithBackupCode(password: String, code: String): AppResult<Unit> =
        runCatchingResult {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            val email = requireNotNull(user.email) { "No email on this account" }
            redeemBackupCode(email, password, code)
            pendingResolver = null
            user.reload().await()
            Unit
        }

    override fun consumeBackupCodeSignInNotice(): Boolean =
        backupCodeSignInNotice.also { backupCodeSignInNotice = false }

    private suspend fun redeemBackupCode(email: String, password: String, code: String) {
        functions.getHttpsCallable("redeemMfaBackupCode")
            .callSafely(name = "redeemMfaBackupCode", data = hashMapOf("email" to email.trim(), "password" to password, "code" to code.trim()))
    }

    private val _signingOut = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val signingOut: kotlinx.coroutines.flow.StateFlow<Boolean> = _signingOut

    override suspend fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        // Not tied to the screen that started it: leaving the screen must not stop
        // a half-done sign-out. Each network step is time-limited, so a slow or
        // missing connection delays sign-out by seconds, never forever.
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val start = System.currentTimeMillis()
                val uid = auth.currentUser?.uid
                com.shahaabapps.core.common.Diag.action("auth.signOut", "uid=${uid ?: "-"}")
                // Detach this device's FCM token from the signed-in account BEFORE
                // signing out (afterwards the delete would be denied).
                if (uid != null) {
                    step("remove alert token") {
                        val token = FirebaseMessaging.getInstance().token.await()
                        messagingTokenRepository.removeToken(uid, token)
                    }
                    // Remove this device's encryption key so children stop sharing with it.
                    step("remove device key") { e2eeManager.unpublishDevice(uid) }
                }
                // Also invalidate the token itself, so a copy that couldn't be removed
                // (e.g. offline) stops delivering the previous account's alerts.
                step("delete alert token") { FirebaseMessaging.getInstance().deleteToken().await() }
                auth.signOut()
                // Drop in-memory E2EE key material and cached place names so a
                // different account on this device starts clean.
                e2eeManager.clearSession()
                backupCodeSignInNotice = false
                runCatching { com.shahaabapps.core.firebase.geofence.GeofenceNameCache.clear(appContext) }
                com.shahaabapps.core.common.Diag.info("auth", "sign-out done in ${System.currentTimeMillis() - start}ms")
            }
        } finally {
            _signingOut.value = false
        }
    }

    /** One best-effort sign-out step, at most [SIGN_OUT_STEP_MS]; a failure or timeout is logged, not fatal. */
    private suspend fun step(name: String, block: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        val done = runCatching { kotlinx.coroutines.withTimeoutOrNull(SIGN_OUT_STEP_MS) { block(); true } }
        val ms = System.currentTimeMillis() - start
        when {
            done.isFailure -> com.shahaabapps.core.common.Diag.warn("auth", "sign-out: $name failed after ${ms}ms", done.exceptionOrNull())
            done.getOrNull() == null -> com.shahaabapps.core.common.Diag.warn("auth", "sign-out: $name timed out after ${ms}ms")
            else -> com.shahaabapps.core.common.Diag.info("auth", "sign-out: $name ok ${ms}ms")
        }
    }

    private companion object {
        const val SIGN_OUT_STEP_MS = 6_000L
    }
}
