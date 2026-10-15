package com.shahaabapps.core.firebase.e2ee

import android.content.Context
import android.util.Base64
import com.shahaabapps.core.common.crypto.DeviceKeyStore
import com.shahaabapps.core.common.crypto.E2ee
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties the device identity ([DeviceKeyStore]) to the backend key exchange
 * ([E2eeKeyRepository]) and holds the child's data key.
 *
 * Child: owns a random data key (persisted sealed under the hardware master key)
 * and wraps it for each linked parent. Parent: unwraps its copy of a child's
 * data key to decrypt that child's payloads. The private key and the plaintext
 * data key never leave the device.
 */
@Singleton
class E2eeManager @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceKeyStore: DeviceKeyStore,
    private val keys: E2eeKeyRepository,
) {
    private val appContext = context.applicationContext
    private val dekLock = Any()
    @Volatile private var cachedDek: ByteArray? = null

    fun myPublicKeyB64(): String = b64(deviceKeyStore.publicKey())
    fun myFingerprint(): String = deviceKeyStore.publicKeyFingerprint()

    // ---- child side ----

    /** The child's data key, generated and persisted (sealed) on first use. */
    fun childDataKey(): ByteArray {
        cachedDek?.let { return it }
        synchronized(dekLock) {
            cachedDek?.let { return it }
            val file = File(File(appContext.filesDir, "e2ee"), "dek.key")
            val existing = if (file.exists()) {
                runCatching { deviceKeyStore.openLocal(file.readBytes()) }.getOrNull()
            } else null
            val dek = existing ?: E2ee.newDataKey().also { fresh ->
                file.parentFile?.mkdirs()
                file.writeBytes(deviceKeyStore.sealLocal(fresh))
            }
            cachedDek = dek
            return dek
        }
    }

    /**
     * Publish the child public key and ensure every linked parent has a wrapped
     * copy of the data key. Safe to call repeatedly; a parent whose public key
     * isn't published yet is simply retried on the next call.
     */
    suspend fun syncChildKeys(childId: String, linkedParents: List<String>) {
        keys.publishChildPublicKey(childId, myPublicKeyB64())
        for (parentUid in linkedParents) ensureWrappedFor(childId, parentUid)
    }

    private suspend fun ensureWrappedFor(childId: String, parentUid: String): Boolean {
        if (keys.hasWrappedKey(childId, parentUid)) return true
        val parentPubB64 = keys.getParentPublicKey(parentUid) ?: return false
        val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(parentPubB64), childDataKey())
        keys.putWrappedKey(childId, parentUid, b64(wrapped), myFingerprint())
        return true
    }

    // ---- parent side ----

    /** Publish this parent's public key so children can wrap their data key for it. */
    suspend fun publishParentKey(parentUid: String) {
        keys.publishParentPublicKey(parentUid, myPublicKeyB64())
    }

    /**
     * Unwrap this parent's copy of [childId]'s data key, or null if it hasn't
     * been wrapped yet (e.g. the child hasn't come online since pairing).
     * [childPublicKeyB64] should come from the pairing QR when available (most
     * trustworthy), else the published child key.
     */
    suspend fun dataKeyForChild(
        childId: String,
        parentUid: String,
        childPublicKeyB64: String?,
    ): ByteArray? {
        val wrapped = keys.getWrappedKey(childId, parentUid) ?: return null
        val childPub = childPublicKeyB64 ?: keys.getChildPublicKey(childId) ?: return null
        return runCatching {
            deviceKeyStore.unwrapDataKeyFrom(unb64(childPub), unb64(wrapped.wrappedB64))
        }.getOrNull()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
