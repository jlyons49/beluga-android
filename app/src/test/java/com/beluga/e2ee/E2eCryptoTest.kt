package com.beluga.e2ee

import com.beluga.e2ee.crypto.E2eCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.zip.Deflater
import javax.crypto.AEADBadTagException

class E2eCryptoTest {

    @Test
    fun testKeyPairGenerationAndSerialization() {
        val keyPair = E2eCrypto.generateKeyPair()
        val pubBytes = E2eCrypto.publicKeyToBytes(keyPair.public)
        assertEquals(49, pubBytes.size)

        val restoredPub = E2eCrypto.bytesToPublicKey(pubBytes)
        val restoredPubBytes = E2eCrypto.publicKeyToBytes(restoredPub)
        assertArrayEquals(pubBytes, restoredPubBytes)

        val privBytes = E2eCrypto.privateKeyToBytes(keyPair.private)
        val restoredPriv = E2eCrypto.bytesToPrivateKey(privBytes)
        val restoredPrivBytes = E2eCrypto.privateKeyToBytes(restoredPriv)
        assertArrayEquals(privBytes, restoredPrivBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPublicKeyLengthRejected() {
        E2eCrypto.bytesToPublicKey(ByteArray(48))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPublicKeyPrefixRejected() {
        val badBytes = ByteArray(49) { 0x04 } // 0x04 is uncompressed, not compressed
        E2eCrypto.bytesToPublicKey(badBytes)
    }

    @Test
    fun testDirectionalSessionMaterialDerivation() {
        val aliceId = E2eCrypto.generateKeyPair()
        val bobId = E2eCrypto.generateKeyPair()

        val aliceEph = E2eCrypto.generateKeyPair()
        val bobEph = E2eCrypto.generateKeyPair()

        val alicePubBytes = E2eCrypto.publicKeyToBytes(aliceId.public)
        val bobPubBytes = E2eCrypto.publicKeyToBytes(bobId.public)
        val aliceEphPubBytes = E2eCrypto.publicKeyToBytes(aliceEph.public)
        val bobEphPubBytes = E2eCrypto.publicKeyToBytes(bobEph.public)

        // Raw ECDH secrets computed by both endpoints
        val aliceSecret = E2eCrypto.performRawEcdh(aliceEph.private, bobEph.public)
        val bobSecret = E2eCrypto.performRawEcdh(bobEph.private, aliceEph.public)
        assertArrayEquals(aliceSecret, bobSecret)

        val aliceSession = E2eCrypto.deriveSessionMaterial(
            sharedSecret = aliceSecret,
            ourIdentityPub = alicePubBytes,
            peerIdentityPub = bobPubBytes,
            ourEphemeralPub = aliceEphPubBytes,
            peerEphemeralPub = bobEphPubBytes
        )

        val bobSession = E2eCrypto.deriveSessionMaterial(
            sharedSecret = bobSecret,
            ourIdentityPub = bobPubBytes,
            peerIdentityPub = alicePubBytes,
            ourEphemeralPub = bobEphPubBytes,
            peerEphemeralPub = aliceEphPubBytes
        )

        // Both parties share the identical 8-byte session ID
        assertEquals(aliceSession.sid, bobSession.sid)

        // Directional keys: Alice's TX key matches Bob's RX key, and vice versa
        assertArrayEquals(aliceSession.txKey, bobSession.rxKey)
        assertArrayEquals(aliceSession.rxKey, bobSession.txKey)

        // Reflection attack defense: TX and RX keys on same device are distinct
        assertFalse(aliceSession.txKey.contentEquals(aliceSession.rxKey))
    }

    @Test
    fun testEncryptDecryptRoundTripWithAad() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val plaintext = "Hello Beluga Secure Air-Gapped Messaging!".toByteArray(Charsets.UTF_8)
        val aad = "v2|mode1|sid123|seq1|total1".toByteArray(Charsets.UTF_8)

        val encrypted = E2eCrypto.encrypt(plaintext, key, aad)
        assertEquals(E2eCrypto.GCM_IV_BYTES, encrypted.iv.size)
        assertEquals(E2eCrypto.GCM_TAG_BYTES, encrypted.tag.size)

        val decrypted = E2eCrypto.decrypt(encrypted.ciphertext, encrypted.iv, encrypted.tag, key, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun testTamperedAadFailsDecryption() {
        val key = ByteArray(32) { 7 }
        val plaintext = "Secret Message".toByteArray(Charsets.UTF_8)
        val aad = "v2|mode1|sid123|seq1|total1".toByteArray(Charsets.UTF_8)
        val tamperedAad = "v2|mode1|sid123|seq2|total1".toByteArray(Charsets.UTF_8)

        val encrypted = E2eCrypto.encrypt(plaintext, key, aad)
        E2eCrypto.decrypt(encrypted.ciphertext, encrypted.iv, encrypted.tag, key, tamperedAad)
    }

    @Test
    fun testIssue1Adler32TrailingZeroByteRegression() {
        // Test vector known to compress to an RFC 1950 zlib stream whose Adler-32 ends in 0x00
        val testVector = byteArrayOf(
            25, 83, 85, 19, 118, -14, -96, -120, 110, 58, -61, -107, -32, -111, 16, 7,
            125, -116, -49, -117, 63, -75, -72, -105, -13, 98, -18, -48, 54, 102, -122, 56
        )
        val compressed = E2eCrypto.zlibCompress(testVector)
        assertEquals("Verify trailing byte of zlib stream is 0x00", 0.toByte(), compressed.last())

        val key = ByteArray(32) { 42 }

        // Encrypt and decrypt — this MUST NOT hang (Issue #1 fix) and must restore exact bytes
        val encrypted = E2eCrypto.encrypt(testVector, key)
        val decrypted = E2eCrypto.decrypt(encrypted.ciphertext, encrypted.iv, encrypted.tag, key)
        assertArrayEquals(testVector, decrypted)
    }

    @Test
    fun testIssue2ZipBombDecompressionCap() {
        // Create an uncompressed block larger than 64 KB (65,536 bytes)
        val bombBytes = ByteArray(70 * 1024) { 'A'.code.toByte() }
        val key = ByteArray(32) { 1 }

        // Compress manually
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(bombBytes)
        deflater.finish()
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            bos.write(buf, 0, count)
        }
        deflater.end()
        val compressedBomb = bos.toByteArray()

        try {
            E2eCrypto.zlibDecompress(compressedBomb)
            fail("Expected GeneralSecurityException on decompression bomb exceeding 64 KB")
        } catch (e: GeneralSecurityException) {
            assertTrue(e.message?.contains("zip bomb defense") == true || e.message?.contains("limit") == true)
        }
    }

    @Test
    fun testMode3HandshakeSignAndVerify() {
        val alice = E2eCrypto.generateKeyPair()
        val bob = E2eCrypto.generateKeyPair()
        val aliceEph = E2eCrypto.generateKeyPair()

        val alicePubBytes = E2eCrypto.publicKeyToBytes(alice.public)
        val bobPubBytes = E2eCrypto.publicKeyToBytes(bob.public)
        val aliceEphPubBytes = E2eCrypto.publicKeyToBytes(aliceEph.public)
        val timestamp = 1792330000L

        val signature = E2eCrypto.signHandshake(
            signingKey = alice.private,
            signerId = "Alice",
            signerIdentityPub = alicePubBytes,
            recipientIdentityPub = bobPubBytes,
            ephemeralPub = aliceEphPubBytes,
            timestampSec = timestamp
        )

        // Valid verification
        val valid = E2eCrypto.verifyHandshake(
            signerPublicKey = alice.public,
            signature = signature,
            signerId = "Alice",
            signerIdentityPub = alicePubBytes,
            recipientIdentityPub = bobPubBytes,
            ephemeralPub = aliceEphPubBytes,
            timestampSec = timestamp
        )
        assertTrue(valid)

        // Verification with wrong recipient must fail
        val evePubBytes = E2eCrypto.publicKeyToBytes(E2eCrypto.generateKeyPair().public)
        val wrongRecipient = E2eCrypto.verifyHandshake(
            signerPublicKey = alice.public,
            signature = signature,
            signerId = "Alice",
            signerIdentityPub = alicePubBytes,
            recipientIdentityPub = evePubBytes,
            ephemeralPub = aliceEphPubBytes,
            timestampSec = timestamp
        )
        assertFalse(wrongRecipient)

        // Verification with tampered timestamp must fail
        val wrongTs = E2eCrypto.verifyHandshake(
            signerPublicKey = alice.public,
            signature = signature,
            signerId = "Alice",
            signerIdentityPub = alicePubBytes,
            recipientIdentityPub = bobPubBytes,
            ephemeralPub = aliceEphPubBytes,
            timestampSec = timestamp + 1
        )
        assertFalse(wrongTs)
    }
}
