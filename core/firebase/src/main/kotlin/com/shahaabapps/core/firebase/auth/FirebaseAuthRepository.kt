package com.shahaabapps.core.firebase.auth

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
) : AuthRepository {

    // Pending state between a sign-in that needs MFA and its resolution, and between
    // beginning TOTP enrollment and confirming it. Singleton repo → single user.
    @Volatile private var pendingResolver: MultiFactorResolver? = null
    @Volatile private var pendingTotpSecret: TotpSecret? = null

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

    override suspend fun signInWithEmail(email: String, password: String): AppResult<SignInOutcome> =
        runCatchingResult {
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
            val result = auth.signInAnonymously().await()
            requireNotNull(result.user?.uid) { "Anonymous sign-in returned null uid" }
        }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> =
        runCatchingResult {
            auth.sendPasswordResetEmail(email).await()
        }

    override fun isMfaEnrolled(): Boolean =
        auth.currentUser?.multiFactor?.enrolledFactors?.isNotEmpty() == true

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    override suspend fun sendEmailVerification(): AppResult<Unit> =
        runCatchingResult {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.sendEmailVerification().await()
            Unit
        }

    override suspend fun reloadUser(): AppResult<Unit> =
        runCatchingResult { auth.currentUser?.reload()?.await(); Unit }

    override suspend fun reauthenticate(password: String): AppResult<Unit> =
        runCatchingResult {
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            val email = requireNotNull(user.email) { "No email on this account" }
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            Unit
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
            val user = requireNotNull(auth.currentUser) { "Not signed in" }
            user.multiFactor.enrolledFactors.forEach { factor ->
                user.multiFactor.unenroll(factor).await()
            }
            Unit
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
        // Drop in-memory E2EE key material so a different account on this device
        // starts clean.
        e2eeManager.clearSession()
    }
}
