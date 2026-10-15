package com.shahaabapps.core.model

import org.json.JSONObject

/**
 * Encodes/decodes the compact JSON carried in the pairing QR code.
 * Shape: {"c":"<childId>","k":"<pairingCode>","p":"<childPublicKey?>"}
 * The "p" field is optional for backward compatibility with older codes.
 */
object PairingCodec {

    fun encode(data: PairingData): String =
        JSONObject()
            .put("c", data.childId)
            .put("k", data.code)
            .apply { data.childPublicKey?.let { put("p", it) } }
            .toString()

    fun decode(raw: String?): PairingData? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            val childId = json.optString("c")
            val code = json.optString("k")
            val childKey = json.optString("p").ifBlank { null }
            if (childId.isBlank() || code.isBlank()) null else PairingData(childId, code, childKey)
        } catch (_: Exception) {
            null
        }
    }
}
