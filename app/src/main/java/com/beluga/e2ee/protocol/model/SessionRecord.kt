package com.beluga.e2ee.protocol.model

/**
 * Representation of an active cryptographic session with a contact.
 *
 * @param userId Contact nickname or display identifier.
 * @param sid 8-byte session ID encoded in RFC 1924 Base85 (10 characters).
 * @param txKeyBase85 32-byte transmission chain key encoded in Base85.
 * @param rxKeyBase85 32-byte reception chain key encoded in Base85.
 * @param txSeq Monotonic sequence counter for outgoing messages (starts at 1).
 * @param lastRxSeq Highest accepted sequence counter for incoming messages (for replay protection).
 * @param establishedAt Epoch timestamp (in milliseconds) when the session was created.
 */
data class SessionRecord(
    val userId: String,
    val sid: String,
    val txKeyBase85: String,
    val rxKeyBase85: String,
    val txSeq: Long,
    val lastRxSeq: Long,
    val establishedAt: Long
)
