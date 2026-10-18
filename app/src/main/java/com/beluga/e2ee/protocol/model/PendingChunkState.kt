package com.beluga.e2ee.protocol.model

/**
 * Tracks received slices of a multi-chunk message (Mode 2) during scanning.
 *
 * @param sid Session ID matching the message.
 * @param seq Sequence number of the multi-chunk message.
 * @param total Total number of chunks expected.
 * @param iv Base85-encoded 12-byte GCM IV.
 * @param tag Base85-encoded 16-byte GCM tag.
 * @param chunks Map of chunk index (0 until total) to Base85-encoded ciphertext slice.
 */
data class PendingChunkState(
    val sid: String,
    val seq: Long,
    val total: Int,
    val iv: String,
    val tag: String,
    val chunks: MutableMap<Int, String> = mutableMapOf()
) {
    val isComplete: Boolean get() = chunks.size == total
    val receivedCount: Int get() = chunks.size
}
