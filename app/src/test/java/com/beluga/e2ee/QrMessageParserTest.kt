package com.beluga.e2ee

import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.model.QrMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrMessageParserTest {

    @Test
    fun testMode6SharePublicKeyRoundTrip() {
        val original = QrMessage.SharePublicKey(
            version = 2,
            userId = "Alice",
            publicKeyBase85 = "0123456789abcdefghijklmnopqrstuvwxyz"
        )
        val serialized = QrMessageParser.serialize(original)
        assertTrue(serialized.contains("\"v\":2"))
        assertTrue(serialized.contains("\"mode\":6"))
        assertTrue(serialized.contains("\"id\":\"Alice\""))

        val parsed = QrMessageParser.parse(serialized) as QrMessage.SharePublicKey
        assertEquals(2, parsed.version)
        assertEquals("Alice", parsed.userId)
        assertEquals(original.publicKeyBase85, parsed.publicKeyBase85)
    }

    @Test
    fun testMode6SharePublicKeyWithoutUserId() {
        val original = QrMessage.SharePublicKey(
            version = 2,
            userId = null,
            publicKeyBase85 = "0123456789abcdef"
        )
        val serialized = QrMessageParser.serialize(original)
        val parsed = QrMessageParser.parse(serialized) as QrMessage.SharePublicKey
        assertEquals(2, parsed.version)
        assertNull(parsed.userId)
        assertEquals(original.publicKeyBase85, parsed.publicKeyBase85)
    }

    @Test
    fun testMode3SessionInitRoundTrip() {
        val original = QrMessage.SessionInit(
            version = 2,
            senderKeyId = "k123456789",
            ephemeralPublicKeyBase85 = "sec_key_payload_85",
            timestampSec = 1792330000L,
            signatureBase85 = "sig_payload_85"
        )
        val serialized = QrMessageParser.serialize(original)
        val parsed = QrMessageParser.parse(serialized) as QrMessage.SessionInit
        assertEquals(2, parsed.version)
        assertEquals(original.senderKeyId, parsed.senderKeyId)
        assertEquals(original.ephemeralPublicKeyBase85, parsed.ephemeralPublicKeyBase85)
        assertEquals(original.timestampSec, parsed.timestampSec)
        assertEquals(original.signatureBase85, parsed.signatureBase85)
    }

    @Test
    fun testMode1SingleChunkRoundTrip() {
        val original = QrMessage.SingleChunk(
            version = 2,
            sid = "sid1234567",
            seq = 42L,
            ivBase85 = "iv_b85_12bytes",
            ciphertextBase85 = "ct_b85_payload",
            tagBase85 = "tag_b85_16bytes"
        )
        val serialized = QrMessageParser.serialize(original)
        val parsed = QrMessageParser.parse(serialized) as QrMessage.SingleChunk
        assertEquals(2, parsed.version)
        assertEquals(original.sid, parsed.sid)
        assertEquals(original.seq, parsed.seq)
        assertEquals(original.ivBase85, parsed.ivBase85)
        assertEquals(original.ciphertextBase85, parsed.ciphertextBase85)
        assertEquals(original.tagBase85, parsed.tagBase85)
    }

    @Test
    fun testMode2MultiChunkRoundTrip() {
        val original = QrMessage.MultiChunk(
            version = 2,
            sid = "sid1234567",
            seq = 7L,
            index = 1,
            total = 4,
            ivBase85 = "iv_b85_12bytes",
            ciphertextBase85 = "ct_slice_b85",
            tagBase85 = "tag_b85_16bytes"
        )
        val serialized = QrMessageParser.serialize(original)
        val parsed = QrMessageParser.parse(serialized) as QrMessage.MultiChunk
        assertEquals(2, parsed.version)
        assertEquals(original.sid, parsed.sid)
        assertEquals(original.seq, parsed.seq)
        assertEquals(1, parsed.index)
        assertEquals(4, parsed.total)
        assertEquals(original.ivBase85, parsed.ivBase85)
        assertEquals(original.ciphertextBase85, parsed.ciphertextBase85)
        assertEquals(original.tagBase85, parsed.tagBase85)
    }

    @Test
    fun testRejectsInvalidVersion() {
        val v1Json = """{"v": 1, "mode": 1, "sid": "s", "seq": 1, "iv": "i", "ct": "c", "tag": "t"}"""
        try {
            QrMessageParser.parse(v1Json)
            fail("Expected IllegalArgumentException for version 1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported protocol version") == true)
        }
    }

    @Test
    fun testRejectsMalformedJson() {
        try {
            QrMessageParser.parse("this is not json")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("JSON") == true)
        }
    }

    @Test
    fun testRejectsMissingRequiredFields() {
        // Missing 'pk' in Mode 6
        val badMode6 = """{"v": 2, "mode": 6, "id": "Alice"}"""
        try {
            QrMessageParser.parse(badMode6)
            fail("Expected IllegalArgumentException for missing pk")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("pk") == true)
        }

        // Mode 2 with index >= total
        val badMode2 = """{"v": 2, "mode": 2, "sid": "s", "seq": 1, "index": 3, "total": 3, "iv": "i", "ct": "c", "tag": "t"}"""
        try {
            QrMessageParser.parse(badMode2)
            fail("Expected IllegalArgumentException for index out of bounds")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("bounds") == true)
        }
    }
}
