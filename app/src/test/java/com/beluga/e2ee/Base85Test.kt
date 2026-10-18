package com.beluga.e2ee

import com.beluga.e2ee.crypto.Base85
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom

class Base85Test {

    @Test
    fun testEmptyData() {
        assertEquals("", Base85.encode(ByteArray(0)))
        assertArrayEquals(ByteArray(0), Base85.decode(""))
    }

    @Test
    fun testKnownValues() {
        // "Hello" -> 5 bytes
        val input = "Hello".toByteArray(Charsets.UTF_8)
        val encoded = Base85.encode(input)
        val decoded = Base85.decode(encoded)
        assertArrayEquals(input, decoded)
    }

    @Test
    fun testMultiplesOf4Bytes() {
        val input = ByteArray(16) { it.toByte() }
        val encoded = Base85.encode(input)
        // 16 bytes -> exactly 20 chars
        assertEquals(20, encoded.length)
        val decoded = Base85.decode(encoded)
        assertArrayEquals(input, decoded)
    }

    @Test
    fun testArbitraryLengthsRoundTrip() {
        val random = SecureRandom(byteArrayOf(1, 2, 3, 4))
        for (len in 1..65) {
            val bytes = ByteArray(len).also { random.nextBytes(it) }
            val encoded = Base85.encode(bytes)
            val decoded = Base85.decode(encoded)
            assertArrayEquals("Failed for length $len", bytes, decoded)
        }
    }
}
