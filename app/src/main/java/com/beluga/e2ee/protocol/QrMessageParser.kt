package com.beluga.e2ee.protocol

import com.beluga.e2ee.protocol.model.QrMessage
import org.json.JSONException
import org.json.JSONObject

/**
 * High-performance parser and serializer for Beluga v2 QR message payloads.
 *
 * Enforces strict protocol version validation (v=2), mode validation, and required field constraints.
 */
object QrMessageParser {

    /**
     * Parses and validates raw JSON payload into a typed [QrMessage].
     *
     * @throws IllegalArgumentException if JSON is malformed, version != 2, or fields are invalid.
     */
    fun parse(rawJson: String): QrMessage {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("QR payload is not a valid JSON object")
        }

        val json = try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed JSON payload: ${e.message}", e)
        }

        if (!json.has("v")) {
            throw IllegalArgumentException("Missing protocol version field 'v'")
        }
        val version = json.optInt("v", -1)
        if (version != 2) {
            throw IllegalArgumentException("Unsupported protocol version: $version (expected v2)")
        }

        if (!json.has("mode")) {
            throw IllegalArgumentException("Missing protocol mode field 'mode'")
        }
        val mode = json.optInt("mode", -1)

        return when (mode) {
            1 -> parseSingleChunk(json)
            2 -> parseMultiChunk(json)
            3 -> parseSessionInit(json)
            6 -> parseSharePublicKey(json)
            else -> throw IllegalArgumentException("Unsupported protocol mode: $mode")
        }
    }

    /**
     * Serializes any [QrMessage] into a compact JSON string suitable for QR encoding.
     */
    fun serialize(msg: QrMessage): String {
        val json = JSONObject()
        val version = when (msg) {
            is QrMessage.SharePublicKey -> msg.version
            is QrMessage.SessionInit -> msg.version
            is QrMessage.SingleChunk -> msg.version
            is QrMessage.MultiChunk -> msg.version
        }
        json.put("v", version)
        when (msg) {
            is QrMessage.SharePublicKey -> {
                json.put("mode", 6)
                msg.userId?.let { json.put("id", it) }
                json.put("pk", msg.publicKeyBase85)
            }
            is QrMessage.SessionInit -> {
                json.put("mode", 3)
                json.put("sender", msg.senderKeyId)
                json.put("sec", msg.ephemeralPublicKeyBase85)
                json.put("ts", msg.timestampSec)
                json.put("sig", msg.signatureBase85)
            }
            is QrMessage.SingleChunk -> {
                json.put("mode", 1)
                json.put("sid", msg.sid)
                json.put("seq", msg.seq)
                json.put("iv", msg.ivBase85)
                json.put("ct", msg.ciphertextBase85)
                json.put("tag", msg.tagBase85)
            }
            is QrMessage.MultiChunk -> {
                json.put("mode", 2)
                json.put("sid", msg.sid)
                json.put("seq", msg.seq)
                json.put("index", msg.index)
                json.put("total", msg.total)
                json.put("iv", msg.ivBase85)
                json.put("ct", msg.ciphertextBase85)
                json.put("tag", msg.tagBase85)
            }
        }
        return json.toString()
    }

    private fun parseSingleChunk(json: JSONObject): QrMessage.SingleChunk {
        val sid = json.optString("sid", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 1 payload missing required 'sid' (session ID)")
        if (!json.has("seq")) {
            throw IllegalArgumentException("Mode 1 payload missing required 'seq' (sequence number)")
        }
        val seq = json.optLong("seq", -1L)
        if (seq <= 0) {
            throw IllegalArgumentException("Mode 1 payload has invalid sequence number: $seq (must be > 0)")
        }
        val iv = json.optString("iv", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 1 payload missing required 'iv' (initialization vector)")
        val ct = json.optString("ct", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 1 payload missing required 'ct' (ciphertext)")
        val tag = json.optString("tag", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 1 payload missing required 'tag' (authentication tag)")

        return QrMessage.SingleChunk(
            version = 2,
            sid = sid,
            seq = seq,
            ivBase85 = iv,
            ciphertextBase85 = ct,
            tagBase85 = tag
        )
    }

    private fun parseMultiChunk(json: JSONObject): QrMessage.MultiChunk {
        val sid = json.optString("sid", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 2 payload missing required 'sid' (session ID)")
        if (!json.has("seq")) {
            throw IllegalArgumentException("Mode 2 payload missing required 'seq' (sequence number)")
        }
        val seq = json.optLong("seq", -1L)
        if (seq <= 0) {
            throw IllegalArgumentException("Mode 2 payload has invalid sequence number: $seq (must be > 0)")
        }
        if (!json.has("total")) {
            throw IllegalArgumentException("Mode 2 payload missing required 'total' chunk count")
        }
        val total = json.optInt("total", -1)
        if (total < 2) {
            throw IllegalArgumentException("Mode 2 payload requires total >= 2, got $total")
        }
        if (total > 64) {
            throw IllegalArgumentException("Mode 2 payload total exceeds maximum 64 chunks: $total")
        }
        if (!json.has("index")) {
            throw IllegalArgumentException("Mode 2 payload missing required chunk 'index'")
        }
        val index = json.optInt("index", -1)
        if (index !in 0 until total) {
            throw IllegalArgumentException("Mode 2 chunk index $index out of bounds (expected 0 until $total)")
        }
        val iv = json.optString("iv", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 2 payload missing required 'iv'")
        val ct = json.optString("ct", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 2 payload missing required 'ct'")
        val tag = json.optString("tag", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 2 payload missing required 'tag'")

        return QrMessage.MultiChunk(
            version = 2,
            sid = sid,
            seq = seq,
            index = index,
            total = total,
            ivBase85 = iv,
            ciphertextBase85 = ct,
            tagBase85 = tag
        )
    }

    private fun parseSessionInit(json: JSONObject): QrMessage.SessionInit {
        val sender = json.optString("sender", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 3 payload missing required 'sender' key ID")
        val sec = json.optString("sec", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 3 payload missing required 'sec' (ephemeral public key)")
        if (!json.has("ts")) {
            throw IllegalArgumentException("Mode 3 payload missing required 'ts' (timestamp)")
        }
        val ts = json.optLong("ts", -1L)
        if (ts <= 0) {
            throw IllegalArgumentException("Mode 3 payload has invalid timestamp: $ts")
        }
        val sig = json.optString("sig", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 3 payload missing required 'sig' (signature)")

        return QrMessage.SessionInit(
            version = 2,
            senderKeyId = sender,
            ephemeralPublicKeyBase85 = sec,
            timestampSec = ts,
            signatureBase85 = sig
        )
    }

    private fun parseSharePublicKey(json: JSONObject): QrMessage.SharePublicKey {
        val id = if (json.has("id") && !json.isNull("id")) {
            json.optString("id", "").takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val pk = json.optString("pk", "").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Mode 6 payload missing required 'pk' (public key)")

        return QrMessage.SharePublicKey(
            version = 2,
            userId = id,
            publicKeyBase85 = pk
        )
    }
}
