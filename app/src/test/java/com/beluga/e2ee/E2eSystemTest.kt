package com.beluga.e2ee

import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.protocol.DecryptedMessageResult
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.HandshakeOutcome
import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.ReplayAttackException
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.storage.DatabaseRepository
import com.beluga.e2ee.storage.EncryptedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class E2eSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createE2eSystem(name: String, password: String): E2eSystem = runBlocking {
        val dbFile = tempFolder.newFile("beluga_$name.enc")
        val repo = DatabaseRepository(EncryptedDatabase(dbFile))
        val keyPair = E2eCrypto.generateKeyPair()
        val signingKeyB85 = Base85.encode(E2eCrypto.privateKeyToBytes(keyPair.private))
        val created = repo.createDatabase(
            password = password,
            initialDisplayName = name,
            initialSigningKey = signingKeyB85
        )
        assertTrue(created)
        E2eSystem(repo)
    }

    @Test
    fun testFullHandshakeAndMessagingFlow() = runBlocking {
        val alice = createE2eSystem("Alice", "alice_pw_123")
        val bob = createE2eSystem("Bob", "bob_pw_456")

        // 1. Share Public Keys (Mode 6)
        val aliceShareJson = alice.shareIdentityKey()
        val bobShareJson = bob.shareIdentityKey()

        val aliceShareMsg = QrMessageParser.parse(aliceShareJson) as QrMessage.SharePublicKey
        val bobShareMsg = QrMessageParser.parse(bobShareJson) as QrMessage.SharePublicKey

        val aliceContactInBob = bob.addOrUpdateContact("Alice", aliceShareMsg)
        val bobContactInAlice = alice.addOrUpdateContact("Bob", bobShareMsg)

        assertEquals("Alice", aliceContactInBob.userId)
        assertEquals("Bob", bobContactInAlice.userId)

        // 2. Alice Initiates Handshake (Mode 3)
        val alicePending = alice.initiateOrResumeHandshake("Bob")
        assertNotNull(alicePending)
        // Verify idempotence (Issue #16 fix: screen rotation returns identical handshake)
        val resumedPending = alice.initiateOrResumeHandshake("Bob", forceNew = false)
        assertEquals(alicePending.ownQrPayload, resumedPending.ownQrPayload)

        // 3. Bob Receives Alice's Mode 3 (Bob has no pending handshake -> Case B)
        val aliceInitMsg = QrMessageParser.parse(alicePending.ownQrPayload) as QrMessage.SessionInit
        val bobOutcome = bob.processSessionInit(aliceInitMsg) as HandshakeOutcome.Completed
        assertFalse(bobOutcome.weInitiated)
        assertEquals("Alice", bobOutcome.contact.userId)

        // 4. Alice Receives Bob's Mode 3 Reply (Alice has pending handshake -> Case A)
        val bobReplyMsg = QrMessageParser.parse(bobOutcome.replyQrJson) as QrMessage.SessionInit
        val aliceOutcome = alice.processSessionInit(bobReplyMsg) as HandshakeOutcome.Completed
        assertTrue(aliceOutcome.weInitiated)
        assertEquals("Bob", aliceOutcome.contact.userId)

        // 5. Verify Cryptographic Agreement
        assertEquals(aliceOutcome.session.sid, bobOutcome.session.sid)
        assertArrayEquals(
            Base85.decode(aliceOutcome.session.txKeyBase85),
            Base85.decode(bobOutcome.session.rxKeyBase85)
        )
        assertArrayEquals(
            Base85.decode(aliceOutcome.session.rxKeyBase85),
            Base85.decode(bobOutcome.session.txKeyBase85)
        )

        // 6. Single-chunk Message Encryption (Mode 1)
        val secretMessage = "Air-gapped secret greeting from Alice to Bob!"
        val encryptedQrs = alice.encryptMessage("Bob", secretMessage)
        assertEquals(1, encryptedQrs.size)

        val mode1Msg = QrMessageParser.parse(encryptedQrs[0]) as QrMessage.SingleChunk
        val decryptedResult = bob.decryptSingleChunk(mode1Msg) as DecryptedMessageResult.Decrypted
        assertEquals("Alice", decryptedResult.contactId)
        assertEquals(1L, decryptedResult.seq)
        assertEquals(secretMessage, decryptedResult.plaintext)

        // 7. Replay Attack Detection
        try {
            bob.decryptSingleChunk(mode1Msg)
            fail("Expected ReplayAttackException on repeated sequence number")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message?.contains("already been received") == true)
        }

        // 8. Multi-chunk Message Encryption (Mode 2)
        val largeMessage = (0..50).joinToString("\n") { index ->
            "Line #$index: Beluga Air-Gapped E2EE Protocol Multi-Chunk Slice Verification Payload ${java.util.UUID.randomUUID()}"
        }
        val multiQrs = alice.encryptMessage("Bob", largeMessage)
        assertTrue("Expected multiple chunks for large message, got ${multiQrs.size}", multiQrs.size > 1)

        val multiMsgs = multiQrs.map { QrMessageParser.parse(it) as QrMessage.MultiChunk }
        // Deliver in shuffled order to test out-of-order reassembly
        val shuffled = multiMsgs.shuffled()
        var finalResult: DecryptedMessageResult? = null

        shuffled.forEachIndexed { idx, chunkMsg ->
            val res = bob.processMultiChunk(chunkMsg)
            if (idx == shuffled.size - 1) {
                finalResult = res
            } else {
                assertTrue(res is DecryptedMessageResult.ChunkProgress)
            }
        }

        assertTrue(finalResult is DecryptedMessageResult.Decrypted)
        val decryptedLarge = (finalResult as DecryptedMessageResult.Decrypted).plaintext
        assertEquals(largeMessage, decryptedLarge)

        // Verify replay on multi-chunk
        try {
            bob.processMultiChunk(multiMsgs.first())
            fail("Expected ReplayAttackException on already completed sequence")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message?.contains("already been received") == true)
        }
    }
}
