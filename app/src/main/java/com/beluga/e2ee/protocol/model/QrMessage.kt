package com.beluga.e2ee.protocol.model

/**
 * Sealed hierarchy representing all valid parsed QR code message types.
 */
sealed class QrMessage {

    /**
     * Mode 6: Share long-term P-384 public key.
     *
     * Example: {"v": 2, "mode": 6, "id": "Alice", "pk": "<Base85>"}
     */
    data class SharePublicKey(
        val version: Int,
        val userId: String?,
        val publicKeyBase85: String
    ) : QrMessage()

    /**
     * Mode 3: Ephemeral authenticated session handshake.
     *
     * Example: {"v": 2, "mode": 3, "sender": "<Base85 8B>", "sec": "<Base85 49B>", "ts": 1792330000, "sig": "<Base85>"}
     */
    data class SessionInit(
        val version: Int,
        val senderKeyId: String,
        val ephemeralPublicKeyBase85: String,
        val timestampSec: Long,
        val signatureBase85: String
    ) : QrMessage()

    /**
     * Mode 1: Single-chunk encrypted message.
     *
     * Example: {"v": 2, "mode": 1, "sid": "<Base85 8B>", "seq": 1, "iv": "<Base85 12B>", "ct": "<Base85>", "tag": "<Base85 16B>"}
     */
    data class SingleChunk(
        val version: Int,
        val sid: String,
        val seq: Long,
        val ivBase85: String,
        val ciphertextBase85: String,
        val tagBase85: String
    ) : QrMessage()

    /**
     * Mode 2: Multi-chunk encrypted message slice.
     *
     * Example: {"v": 2, "mode": 2, "sid": "<Base85 8B>", "seq": 1, "index": 0, "total": 3, "iv": "<Base85 12B>", "ct": "<Base85>", "tag": "<Base85 16B>"}
     */
    data class MultiChunk(
        val version: Int,
        val sid: String,
        val seq: Long,
        val index: Int,
        val total: Int,
        val ivBase85: String,
        val ciphertextBase85: String,
        val tagBase85: String
    ) : QrMessage()
}
