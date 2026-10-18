package com.beluga.e2ee.protocol.model

/**
 * Representation of a verified or added contact.
 *
 * @param userId Contact nickname or display identifier.
 * @param publicKeyBase85 49-byte compressed P-384 public key encoded in RFC 1924 Base85.
 * @param addedAt Epoch timestamp (in milliseconds) when the contact was added.
 * @param verified True if the user has manually verified the contact's 60-digit safety number.
 */
data class Contact(
    val userId: String,
    val publicKeyBase85: String,
    val addedAt: Long,
    val verified: Boolean = false
)
