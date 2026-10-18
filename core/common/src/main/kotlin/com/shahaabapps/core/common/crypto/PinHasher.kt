package com.shahaabapps.core.common.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted, slow hashing for the uninstall removal PIN, so the PIN is never stored
 * or synced in plaintext. The parent hashes when setting the PIN; the child
 * verifies an entered PIN against the stored salt+hash locally.
 *
 * PBKDF2-HMAC-SHA256 with a per-PIN random salt. Note the backend still stores
 * the hash where the child's own account can read it, so this raises the bar
 * (offline brute-force only) rather than eliminating it — true protection would
 * verify server-side. Given the PIN is 15–20 digits, PBKDF2 at this cost makes
 * offline cracking expensive.
 */
object PinHasher {
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val rng = SecureRandom()

    /** Returns (saltB64, hashB64) for [pin]. */
    fun hash(pin: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        return b64(salt) to b64(derive(pin, salt))
    }

    /** Constant-time verify of [pin] against a stored [saltB64]/[hashB64]. */
    fun verify(pin: String, saltB64: String, hashB64: String): Boolean {
        if (saltB64.isBlank() || hashB64.isBlank()) return false
        return runCatching {
            val expected = unb64(hashB64)
            val actual = derive(pin, unb64(saltB64))
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
