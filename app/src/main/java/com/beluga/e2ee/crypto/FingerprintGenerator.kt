package com.beluga.e2ee.crypto

import java.security.MessageDigest
import java.util.Locale

/**
 * Key fingerprint and safety number generator for human-verifiable key verification.
 */
object FingerprintGenerator {

    private const val PREFIX = "BELUGA-ID-v2"
    private const val INFO_SAFETY = "BELUGA-SAFETY-NUMBER"

    /**
     * Derives a 60-digit safety number formatted as 12 groups of 5 decimal digits (similar to Signal).
     *
     * Computes SHA-256("BELUGA-ID-v2" || publicKeyBytes), expands via HKDF-SHA256 to 48 bytes,
     * and maps each 4-byte big-endian unsigned integer modulo 100,000 into a 5-digit string.
     *
     * @param publicKeyBytes The 49-byte compressed P-384 public key.
     * @param multiline If true, formats as 3 lines of 4 groups; otherwise single space-separated string.
     */
    fun generateSafetyNumber(publicKeyBytes: ByteArray, multiline: Boolean = false): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(PREFIX.toByteArray(Charsets.UTF_8) + publicKeyBytes)
        val expanded = HkdfSha256.derive(
            ikm = hash,
            salt = null,
            info = INFO_SAFETY.toByteArray(Charsets.UTF_8),
            length = 48
        )
        val groups = ArrayList<String>(12)
        for (i in 0 until 12) {
            val offset = i * 4
            val v = ((expanded[offset].toLong() and 0xFF) shl 24) or
                    ((expanded[offset + 1].toLong() and 0xFF) shl 16) or
                    ((expanded[offset + 2].toLong() and 0xFF) shl 8) or
                    (expanded[offset + 3].toLong() and 0xFF)
            groups.add(String.format(Locale.US, "%05d", v % 100000))
        }
        return if (multiline) {
            groups.chunked(4).joinToString("\n") { it.joinToString(" ") }
        } else {
            groups.joinToString(" ")
        }
    }

    /**
     * Generates a 10-character Base85 short key ID from the first 8 bytes of the SHA-256 identity hash.
     * Used for fast sender lookup in Mode 3 handshake packets and compact UI badges.
     */
    fun generateShortKeyId(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(PREFIX.toByteArray(Charsets.UTF_8) + publicKeyBytes)
        return Base85.encode(hash.copyOf(8))
    }
}
