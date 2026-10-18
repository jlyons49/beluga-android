package com.beluga.e2ee.crypto

/**
 * RFC 1924 Base85 codec compatible with Python's base64.b85encode and b85decode.
 *
 * Alphabet: 0-9 A-Z a-z !#$%&()*+-;<=>?@^_`{|}~ (85 characters)
 * Encodes 4 bytes -> 5 chars without line breaks.
 * Partial input blocks are handled per RFC (output length ceil(n * 5 / 4)).
 */
object Base85 {

    private const val ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~"

    private val ENCODE_TABLE: ByteArray = ALPHABET.toByteArray(Charsets.US_ASCII)

    // '~' is at index 84; used to pad partial decode groups per RFC 1924
    private val FILL_BYTE: Byte = ENCODE_TABLE[84]

    private val DECODE_TABLE = IntArray(256) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val padding = (4 - data.size % 4) % 4
        val padded = if (padding > 0) data + ByteArray(padding) else data
        val outputLen = (data.size * 5 + 3) / 4 // ceil(n * 5 / 4)
        val out = ByteArray(outputLen)
        var outIdx = 0

        for (i in padded.indices step 4) {
            // Read 4 bytes as big-endian unsigned 32-bit integer
            var acc = ((padded[i].toLong() and 0xFF) shl 24) or
                      ((padded[i + 1].toLong() and 0xFF) shl 16) or
                      ((padded[i + 2].toLong() and 0xFF) shl 8) or
                      (padded[i + 3].toLong() and 0xFF)

            val chars = ByteArray(5)
            for (j in 4 downTo 0) {
                chars[j] = ENCODE_TABLE[(acc % 85).toInt()]
                acc /= 85
            }
            for (j in 0..4) {
                if (outIdx < outputLen) out[outIdx++] = chars[j]
            }
        }
        return out.toString(Charsets.US_ASCII)
    }

    fun decode(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)
        val inputLen = encoded.length
        val padding = (5 - inputLen % 5) % 5
        val outputLen = inputLen * 4 / 5 // floor(n * 4 / 5)

        val bytes = encoded.toByteArray(Charsets.US_ASCII)
        val padded = if (padding > 0) bytes + ByteArray(padding) { FILL_BYTE } else bytes

        val out = ByteArray(padded.size / 5 * 4)
        var outIdx = 0

        for (i in padded.indices step 5) {
            var acc = 0L
            for (j in 0..4) {
                val digit = DECODE_TABLE[padded[i + j].toInt() and 0xFF]
                require(digit >= 0) { "Invalid base85 character at index ${i + j}" }
                acc = acc * 85 + digit
            }
            out[outIdx++] = (acc shr 24).toByte()
            out[outIdx++] = (acc shr 16).toByte()
            out[outIdx++] = (acc shr 8).toByte()
            out[outIdx++] = acc.toByte()
        }
        return out.copyOf(outputLen)
    }
}
