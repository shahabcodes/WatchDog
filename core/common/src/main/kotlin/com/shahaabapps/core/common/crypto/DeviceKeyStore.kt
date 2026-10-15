package com.shahaabapps.core.common.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This device's long-lived E2EE identity keypair (P-256), plus local secret
 * storage for the child's data key.
 *
 * The private key never leaves the device and is never uploaded: it is stored
 * on disk encrypted with AES-256-GCM under a non-exportable **master key** held
 * in the Android Keystore (hardware-backed / StrongBox where available). Because
 * `PURPOSE_AGREE_KEY` only exists on API 31+, the EC key itself lives in an
 * app-private file (wrapped by the master key) rather than directly in the
 * Keystore, so key agreement works uniformly down to minSdk 26. Backend
 * compromise therefore can never recover a private key or a plaintext payload.
 */
@Singleton
class DeviceKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    @Volatile private var cachedPublic: ByteArray? = null
    @Volatile private var cachedPrivate: ByteArray? = null

    /** This device's public key (X.509 SPKI bytes), safe to publish. */
    fun publicKey(): ByteArray = ensureIdentity().first

    /** A short fingerprint of this device's public key, for out-of-band verification. */
    fun publicKeyFingerprint(): String = E2ee.fingerprint(publicKey())

    /** ECDH shared secret with a peer's public key (X.509 SPKI bytes). */
    fun sharedSecret(peerPublicKey: ByteArray): ByteArray {
        val priv = E2ee.privateKeyFromBytes(ensureIdentity().second)
        val peer = E2ee.publicKeyFromBytes(peerPublicKey)
        return E2ee.sharedSecret(priv, peer)
    }

    /** Wrap [dataKey] for [peerPublicKey] so only that peer can unwrap it. */
    fun wrapDataKeyFor(peerPublicKey: ByteArray, dataKey: ByteArray): ByteArray =
        E2ee.wrapDataKey(dataKey, sharedSecret(peerPublicKey), publicKey(), peerPublicKey)

    /** Unwrap a data key [wrapped] by [peerPublicKey]. */
    fun unwrapDataKeyFrom(peerPublicKey: ByteArray, wrapped: ByteArray): ByteArray =
        E2ee.unwrapDataKey(wrapped, sharedSecret(peerPublicKey), publicKey(), peerPublicKey)

    /** Encrypt [bytes] for on-device storage under the hardware-backed master key. */
    fun sealLocal(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = cipher.doFinal(bytes)
        return cipher.iv + ct // Keystore GCM IV is 12 bytes
    }

    /** Decrypt bytes produced by [sealLocal]. */
    fun openLocal(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    // ---- identity persistence ----

    private fun ensureIdentity(): Pair<ByteArray, ByteArray> {
        cachedPublic?.let { pub -> cachedPrivate?.let { priv -> return pub to priv } }
        synchronized(lock) {
            cachedPublic?.let { pub -> cachedPrivate?.let { priv -> return pub to priv } }
            val file = identityFile()
            if (file.exists()) {
                runCatching { readIdentity(file) }.getOrNull()?.let {
                    cachedPublic = it.first; cachedPrivate = it.second
                    return it
                }
            }
            // First run (or unreadable): generate and persist a new identity.
            val kp = E2ee.newKeyPair()
            val pub = E2ee.publicKeyBytes(kp.public)
            val priv = E2ee.privateKeyBytes(kp.private)
            writeIdentity(file, pub, priv)
            cachedPublic = pub; cachedPrivate = priv
            return pub to priv
        }
    }

    private fun readIdentity(file: File): Pair<ByteArray, ByteArray> =
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unknown identity format" }
            val pub = ByteArray(input.readInt()).also { input.readFully(it) }
            val wrapped = ByteArray(input.readInt()).also { input.readFully(it) }
            pub to openLocal(wrapped)
        }

    private fun writeIdentity(file: File, pub: ByteArray, priv: ByteArray) {
        val wrapped = sealLocal(priv)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(pub.size); out.write(pub)
            out.writeInt(wrapped.size); out.write(wrapped)
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true); tmp.delete()
        }
    }

    private fun identityFile(): File = File(File(context.filesDir, "e2ee"), "device.key")

    // ---- master key (Android Keystore) ----

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generateMasterKey(strongBox = true)
    }

    private fun generateMasterKey(strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (strongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        return try {
            gen.init(spec)
            gen.generateKey()
        } catch (e: android.security.keystore.StrongBoxUnavailableException) {
            // No secure element — fall back to the TEE-backed Keystore.
            generateMasterKey(strongBox = false)
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "wd_e2ee_master_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val FORMAT_VERSION = 1
    }
}
