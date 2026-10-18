package com.beluga.e2ee

import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.crypto.FingerprintGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintGeneratorTest {

    @Test
    fun testSafetyNumberFormat() {
        val keyPair = E2eCrypto.generateKeyPair()
        val pubBytes = E2eCrypto.publicKeyToBytes(keyPair.public)

        val safetyNumber = FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = false)
        val groups = safetyNumber.split(" ")
        assertEquals(12, groups.size)
        for (group in groups) {
            assertEquals(5, group.length)
            assertTrue(group.all { it.isDigit() })
        }
        // Total digits = 60
        assertEquals(60, groups.joinToString("").length)

        val multiline = FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = true)
        val lines = multiline.split("\n")
        assertEquals(3, lines.size)
        for (line in lines) {
            val lineGroups = line.split(" ")
            assertEquals(4, lineGroups.size)
        }
    }

    @Test
    fun testShortKeyId() {
        val keyPair = E2eCrypto.generateKeyPair()
        val pubBytes = E2eCrypto.publicKeyToBytes(keyPair.public)

        val shortId = FingerprintGenerator.generateShortKeyId(pubBytes)
        // 8 bytes in Base85 is exactly 10 characters
        assertEquals(10, shortId.length)

        // Deterministic
        val shortId2 = FingerprintGenerator.generateShortKeyId(pubBytes)
        assertEquals(shortId, shortId2)
    }
}
