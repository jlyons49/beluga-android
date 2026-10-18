package com.beluga.e2ee.protocol.model

/**
 * Persisted state of an incomplete or outgoing Mode 3 session handshake.
 *
 * Scoped per userId to prevent cross-contact key contamination (Issue #5) and
 * survive screen rotations and backgrounding (Issue #16).
 *
 * @param userId Target contact identifier.
 * @param ephemeralPrivateKeyBase85 Ephemeral P-384 private key (PKCS#8 DER) in Base85.
 * @param ephemeralPublicKeyBase85 Ephemeral P-384 public key (49-byte compressed) in Base85.
 * @param ownQrPayload Full Mode 3 JSON string rendered in our QR code for this contact.
 * @param createdAt Epoch timestamp (in milliseconds) when the handshake was initiated.
 */
data class PendingHandshake(
    val userId: String,
    val ephemeralPrivateKeyBase85: String,
    val ephemeralPublicKeyBase85: String,
    val ownQrPayload: String,
    val createdAt: Long
)
