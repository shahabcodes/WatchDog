package com.fms.parentalcontrol.core.model

import org.json.JSONObject

/**
 * Encodes/decodes the compact JSON carried in the pairing QR code.
 * Shape: {"c":"<childId>","k":"<pairingCode>"}
 */
object PairingCodec {

    fun encode(data: PairingData): String =
        JSONObject()
            .put("c", data.childId)
            .put("k", data.code)
            .toString()

    fun decode(raw: String?): PairingData? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            val childId = json.optString("c")
            val code = json.optString("k")
            if (childId.isBlank() || code.isBlank()) null else PairingData(childId, code)
        } catch (_: Exception) {
            null
        }
    }
}
