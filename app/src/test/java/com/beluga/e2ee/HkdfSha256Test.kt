package com.beluga.e2ee

import com.beluga.e2ee.crypto.HkdfSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HkdfSha256Test {

    @Test
    fun testDeriveLength() {
        val ikm = "InputKeyMaterial".toByteArray(Charsets.UTF_8)
        val salt = "SaltBytes".toByteArray(Charsets.UTF_8)
        val info = "InfoBytes".toByteArray(Charsets.UTF_8)

        val out32 = HkdfSha256.derive(ikm, salt, info, 32)
        assertEquals(32, out32.size)

        val out64 = HkdfSha256.derive(ikm, salt, info, 64)
        assertEquals(64, out64.size)

        val out8 = HkdfSha256.derive(ikm, salt, info, 8)
        assertEquals(8, out8.size)
    }

    @Test
    fun testNullSaltEqualsZeroSalt() {
        val ikm = "SampleIKM".toByteArray(Charsets.UTF_8)
        val info = "SampleInfo".toByteArray(Charsets.UTF_8)

        val withNullSalt = HkdfSha256.derive(ikm, null, info, 32)
        val withZeroSalt = HkdfSha256.derive(ikm, ByteArray(32), info, 32)
        assert(withNullSalt.contentEquals(withZeroSalt))
    }

    @Test
    fun testDifferentInfoProducesDifferentKeys() {
        val ikm = "SharedSecret".toByteArray(Charsets.UTF_8)
        val key1 = HkdfSha256.derive(ikm, null, "INFO-A".toByteArray(Charsets.UTF_8), 32)
        val key2 = HkdfSha256.derive(ikm, null, "INFO-B".toByteArray(Charsets.UTF_8), 32)
        assertFalse(key1.contentEquals(key2))
    }
}
