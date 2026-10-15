package com.shahaabapps.core.common.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption with AES-256-GCM. The 12-byte random IV is prefixed
 * to the ciphertext (which already carries the 16-byte GCM tag), so a sealed
 * blob is self-describing: `iv(12) || ciphertext || tag`.
 *
 * Pure JVM: the key is supplied as raw bytes, so this works for both the
 * data-key payload encryption and the key-encryption-key wrapping.
 */
internal object Aead {
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AEAD key must be 32 bytes" }
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AEAD key must be 32 bytes" }
        require(blob.size > IV_LEN) { "Ciphertext too short" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ct)
    }
}
