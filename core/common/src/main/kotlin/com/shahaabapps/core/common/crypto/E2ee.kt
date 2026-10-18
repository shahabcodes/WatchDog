package com.shahaabapps.core.common.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * End-to-end encryption primitives, using envelope encryption:
 *
 *  - Each child owns a random 256-bit **data key (DEK)** and encrypts every
 *    sensitive payload under it with AES-256-GCM ([sealPayload]).
 *  - The DEK is **wrapped** (encrypted) once per linked parent ([wrapDataKey])
 *    using a key-encryption-key derived from an ECDH(P-256) shared secret
 *    between the two devices' keypairs. Only the wrapped DEK and the public
 *    keys ever reach the backend.
 *  - A parent **unwraps** the DEK with its own private key ([unwrapDataKey]),
 *    then decrypts payloads ([openPayload]).
 *
 * Keys agree because ECDH(childPriv, parentPub) == ECDH(parentPriv, childPub),
 * and the KEK is bound to the specific pair of public keys (via the HKDF salt),
 * so a wrapped DEK is only usable by that exact parent device.
 *
 * All functions here are pure (no Android APIs); private keys are supplied as
 * bytes by [DeviceKeyStore], which keeps them hardware-protected at rest.
 */
object E2ee {
    private const val CURVE = "secp256r1" // NIST P-256
    private const val KEK_INFO = "watch-dog/e2ee/kek/v1"
    private val rng = SecureRandom()

    private val unsignedLex = Comparator<ByteArray> { a, b ->
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return@Comparator d
        }
        a.size - b.size
    }

    /** A fresh random 256-bit data key. */
    fun newDataKey(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    /** A new P-256 keypair for a device identity. */
    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE))
        }.generateKeyPair()

    fun publicKeyBytes(key: PublicKey): ByteArray = key.encoded // X.509 SubjectPublicKeyInfo
    fun privateKeyBytes(key: PrivateKey): ByteArray = key.encoded // PKCS#8

    fun publicKeyFromBytes(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

    fun privateKeyFromBytes(bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))

    /** A short, human-comparable fingerprint of a public key (first 8 bytes of its SHA-256, hex). */
    fun fingerprint(publicKey: ByteArray): String =
        sha256(publicKey).take(8).joinToString("") { "%02x".format(it) }

    /** Raw ECDH shared secret between our private key and the peer's public key. */
    fun sharedSecret(myPrivate: PrivateKey, peerPublic: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(myPrivate)
            doPhase(peerPublic, true)
            generateSecret()
        }

    /**
     * Derive the pairwise key-encryption key. Symmetric in the two public keys
     * (sorted before hashing) so both devices compute the same KEK regardless of
     * which one is wrapping.
     */
    fun deriveKek(sharedSecret: ByteArray, myPublic: ByteArray, peerPublic: ByteArray): ByteArray {
        val (a, b) = listOf(myPublic, peerPublic).sortedWith(unsignedLex)
        val salt = sha256(a + b)
        return Hkdf.derive(sharedSecret, salt, KEK_INFO.toByteArray(Charsets.UTF_8), 32)
    }

    /** Wrap [dataKey] so only the holder of [peerPublic]'s private key can unwrap it. */
    fun wrapDataKey(
        dataKey: ByteArray,
        sharedSecret: ByteArray,
        myPublic: ByteArray,
        peerPublic: ByteArray,
    ): ByteArray = Aead.seal(deriveKek(sharedSecret, myPublic, peerPublic), dataKey)

    /** Recover a data key wrapped by the peer. */
    fun unwrapDataKey(
        wrapped: ByteArray,
        sharedSecret: ByteArray,
        myPublic: ByteArray,
        peerPublic: ByteArray,
    ): ByteArray = Aead.open(deriveKek(sharedSecret, myPublic, peerPublic), wrapped)

    /**
     * Encrypt a payload under the data key, optionally binding it to [aad]
     * (associated data, e.g. the childId) so the ciphertext can't be
     * cut-and-pasted into a different context and still authenticate.
     */
    fun sealPayload(dataKey: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray =
        Aead.seal(dataKey, plaintext, aad)

    /** Decrypt a payload under the data key, verifying the same [aad] used to seal. */
    fun openPayload(dataKey: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray =
        Aead.open(dataKey, blob, aad)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
