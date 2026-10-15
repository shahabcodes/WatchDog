package com.shahaabapps.core.common.crypto

import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869). Used to turn a raw ECDH shared secret into a
 * fixed-length, uniformly random key-encryption key. Pure JVM — no Android
 * dependency — so it is unit-testable and identical on both devices.
 */
internal object Hkdf {
    private const val HASH_LEN = 32
    private const val ALGO = "HmacSHA256"

    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "Invalid HKDF length: $length" }
        val mac = Mac.getInstance(ALGO)

        // Extract: PRK = HMAC(salt, ikm). An empty salt becomes a zero block.
        val saltKey = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        mac.init(SecretKeySpec(saltKey, ALGO))
        val prk = mac.doFinal(ikm)

        // Expand.
        mac.init(SecretKeySpec(prk, ALGO))
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }
}
