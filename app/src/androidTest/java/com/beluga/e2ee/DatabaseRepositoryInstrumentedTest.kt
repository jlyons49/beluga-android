package com.beluga.e2ee

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.crypto.FingerprintGenerator
import com.beluga.e2ee.protocol.DecryptedMessageResult
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.HandshakeOutcome
import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.ReplayAttackException
import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.protocol.model.SessionRecord
import com.beluga.e2ee.qr.QrEncoder
import com.beluga.e2ee.storage.DatabaseRepository
import com.beluga.e2ee.storage.EncryptedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests running on an Android runtime device or emulator.
 *
 * Verifies:
 * 1. [DatabaseRepository] lifecycle, master password authentication, encryption, and persistence.
 * 2. [E2eSystem] complete cryptographic handshake, single/multi-chunk messaging, replay protection,
 *    and safety numbers on real Android file storage.
 * 3. [QrEncoder] Android [Bitmap] rasterization and pixel encoding.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRepositoryInstrumentedTest {

    private lateinit var context: Context
    private val testDbFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cleanupTestFiles()
    }

    @After
    fun tearDown() {
        cleanupTestFiles()
    }

    private fun cleanupTestFiles() {
        val defaultFiles = listOf(
            File(context.filesDir, EncryptedDatabase.DB_FILE_NAME),
            File(context.filesDir, "${EncryptedDatabase.DB_FILE_NAME}.tmp"),
            File(context.filesDir, "${EncryptedDatabase.DB_FILE_NAME}.bak")
        )
        for (f in defaultFiles) {
            if (f.exists()) f.delete()
        }
        for (f in testDbFiles) {
            val tmp = File(f.parentFile, "${f.name}.tmp")
            val bak = File(f.parentFile, "${f.name}.bak")
            if (f.exists()) f.delete()
            if (tmp.exists()) tmp.delete()
            if (bak.exists()) bak.delete()
        }
        testDbFiles.clear()
    }

    private fun createTestRepository(dbFileName: String): DatabaseRepository {
        val file = File(context.filesDir, dbFileName)
        testDbFiles.add(file)
        return DatabaseRepository(EncryptedDatabase(file))
    }

    @Test
    fun testDatabaseCreationLockAndUnlock() = runBlocking {
        val repo = DatabaseRepository(context) // uses default database.bin
        assertFalse("Database should not exist before creation", repo.exists())

        val password = "StrongMasterPassword99#"
        val keyPair = E2eCrypto.generateKeyPair()
        val signingKeyB85 = Base85.encode(E2eCrypto.privateKeyToBytes(keyPair.private))

        val created = repo.createDatabase(
            password = password,
            initialDisplayName = "Alice Device",
            initialSigningKey = signingKeyB85
        )
        assertTrue("createDatabase should return true", created)
        assertTrue("Database file should now exist", repo.exists())
        assertTrue("Repository should be unlocked after creation", repo.isUnlocked.value)
        assertEquals("Alice Device", repo.displayName.value)
        assertEquals(signingKeyB85, repo.signingKey.value)

        // Lock database
        repo.lock()
        assertFalse("Repository should be locked", repo.isUnlocked.value)
        assertNull("Display name should be wiped on lock", repo.displayName.value)
        assertNull("Signing key should be wiped on lock", repo.signingKey.value)

        // Attempt unlock with incorrect password
        val wrongUnlock = repo.unlock("WrongPassword123")
        assertFalse("Unlock with wrong password must fail", wrongUnlock)
        assertFalse("Repository must remain locked", repo.isUnlocked.value)

        // Unlock with correct password
        val correctUnlock = repo.unlock(password)
        assertTrue("Unlock with correct password must succeed", correctUnlock)
        assertTrue("Repository must be unlocked", repo.isUnlocked.value)
        assertEquals("Alice Device", repo.displayName.value)
        assertEquals(signingKeyB85, repo.signingKey.value)
    }

    @Test
    fun testContactsAndSessionPersistence() = runBlocking {
        val repo = createTestRepository("test_persist.bin")
        val password = "PersistentPassword1!"
        val keyPair = E2eCrypto.generateKeyPair()
        val signingKeyB85 = Base85.encode(E2eCrypto.privateKeyToBytes(keyPair.private))

        repo.createDatabase(
            password = password,
            initialDisplayName = "PersistenceTester",
            initialSigningKey = signingKeyB85
        )

        val peerPair = E2eCrypto.generateKeyPair()
        val peerPublicB85 = Base85.encode(E2eCrypto.publicKeyToBytes(peerPair.public))

        val contact = Contact(
            userId = "Bob",
            publicKeyBase85 = peerPublicB85,
            addedAt = System.currentTimeMillis(),
            verified = true
        )
        repo.saveContact(contact)

        val session = SessionRecord(
            userId = "Bob",
            sid = "test-sid-12345",
            txKeyBase85 = Base85.encode(ByteArray(32) { 1 }),
            rxKeyBase85 = Base85.encode(ByteArray(32) { 2 }),
            txSeq = 5L,
            lastRxSeq = 3L,
            establishedAt = System.currentTimeMillis()
        )
        repo.saveSession(session)

        // Lock and reload repository from the same file
        repo.lock()
        val reloadedRepo = createTestRepository("test_persist.bin")
        assertTrue(reloadedRepo.exists())
        assertTrue(reloadedRepo.unlock(password))

        val loadedContact = reloadedRepo.getContact("Bob")
        assertNotNull(loadedContact)
        assertEquals("Bob", loadedContact?.userId)
        assertEquals(peerPublicB85, loadedContact?.publicKeyBase85)
        assertTrue(loadedContact?.verified == true)

        val loadedSession = reloadedRepo.getSession("Bob")
        assertNotNull(loadedSession)
        assertEquals("test-sid-12345", loadedSession?.sid)
        assertEquals(5L, loadedSession?.txSeq)
        assertEquals(3L, loadedSession?.lastRxSeq)
    }

    @Test
    fun testE2eSystemCompleteFlowOnAndroidRuntime() = runBlocking {
        val aliceRepo = createTestRepository("alice_e2e.bin")
        val bobRepo = createTestRepository("bob_e2e.bin")

        val aliceKey = E2eCrypto.generateKeyPair()
        val bobKey = E2eCrypto.generateKeyPair()

        aliceRepo.createDatabase(
            password = "alice_pw",
            initialDisplayName = "Alice",
            initialSigningKey = Base85.encode(E2eCrypto.privateKeyToBytes(aliceKey.private))
        )
        bobRepo.createDatabase(
            password = "bob_pw",
            initialDisplayName = "Bob",
            initialSigningKey = Base85.encode(E2eCrypto.privateKeyToBytes(bobKey.private))
        )

        val aliceSystem = E2eSystem(aliceRepo)
        val bobSystem = E2eSystem(bobRepo)

        // 1. Share Public Keys (Mode 6)
        val aliceShareMsg = QrMessageParser.parse(aliceSystem.shareIdentityKey()) as QrMessage.SharePublicKey
        val bobShareMsg = QrMessageParser.parse(bobSystem.shareIdentityKey()) as QrMessage.SharePublicKey

        val aliceInBob = bobSystem.addOrUpdateContact("Alice", aliceShareMsg)
        val bobInAlice = aliceSystem.addOrUpdateContact("Bob", bobShareMsg)

        assertEquals("Alice", aliceInBob.userId)
        assertEquals("Bob", bobInAlice.userId)

        // Verify Safety Numbers Match
        val aliceSafetyForBob = FingerprintGenerator.generateSafetyNumber(Base85.decode(bobInAlice.publicKeyBase85))
        val bobOwnSafety = bobSystem.getMySafetyNumber()
        assertEquals(aliceSafetyForBob, bobOwnSafety)

        val bobSafetyForAlice = FingerprintGenerator.generateSafetyNumber(Base85.decode(aliceInBob.publicKeyBase85))
        val aliceOwnSafety = aliceSystem.getMySafetyNumber()
        assertEquals(bobSafetyForAlice, aliceOwnSafety)

        // 2. Handshake (Mode 3)
        val alicePending = aliceSystem.initiateOrResumeHandshake("Bob")
        val aliceInitMsg = QrMessageParser.parse(alicePending.ownQrPayload) as QrMessage.SessionInit

        val bobOutcome = bobSystem.processSessionInit(aliceInitMsg) as HandshakeOutcome.Completed
        assertFalse(bobOutcome.weInitiated)

        val bobReplyMsg = QrMessageParser.parse(bobOutcome.replyQrJson) as QrMessage.SessionInit
        val aliceOutcome = aliceSystem.processSessionInit(bobReplyMsg) as HandshakeOutcome.Completed
        assertTrue(aliceOutcome.weInitiated)

        assertEquals(aliceOutcome.session.sid, bobOutcome.session.sid)
        assertArrayEquals(
            Base85.decode(aliceOutcome.session.txKeyBase85),
            Base85.decode(bobOutcome.session.rxKeyBase85)
        )

        // 3. Single-chunk Messaging (Mode 1)
        val msg1 = "Secret hello from Alice on Android!"
        val qrs1 = aliceSystem.encryptMessage("Bob", msg1)
        assertEquals(1, qrs1.size)

        val mode1 = QrMessageParser.parse(qrs1[0]) as QrMessage.SingleChunk
        val decResult1 = bobSystem.decryptSingleChunk(mode1) as DecryptedMessageResult.Decrypted
        assertEquals("Alice", decResult1.contactId)
        assertEquals(1L, decResult1.seq)
        assertEquals(msg1, decResult1.plaintext)

        // 4. Replay Attack Detection
        try {
            bobSystem.decryptSingleChunk(mode1)
            fail("Replaying seq 1 must throw ReplayAttackException")
        } catch (_: ReplayAttackException) {
            // Expected
        }

        // 5. Multi-chunk Messaging (Mode 2)
        val largeMessage = "Beluga Android Large Payload: " + "X".repeat(1200)
        val qrs2 = aliceSystem.encryptMessage("Bob", largeMessage)
        assertTrue("Multi-chunk must produce >= 2 QR codes", qrs2.size > 1)

        var finalDecrypted: DecryptedMessageResult.Decrypted? = null
        for (qrJson in qrs2) {
            val chunk = QrMessageParser.parse(qrJson) as QrMessage.MultiChunk
            when (val res = bobSystem.processMultiChunk(chunk)) {
                is DecryptedMessageResult.Decrypted -> {
                    finalDecrypted = res
                }
                is DecryptedMessageResult.ChunkProgress -> {
                    // Continuing
                }
            }
        }

        assertNotNull("Reassembled message must not be null", finalDecrypted)
        assertEquals(largeMessage, finalDecrypted?.plaintext)
    }

    @Test
    fun testQrEncoderOnAndroid() = runBlocking {
        val testContent = "{\"v\":2,\"mode\":6,\"id\":\"Tester\"}"

        // Test synchronous encoding
        val bitmapSync = QrEncoder.encodeSync(testContent, sizePx = 256)
        assertNotNull(bitmapSync)
        assertEquals(256, bitmapSync.width)
        assertEquals(256, bitmapSync.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmapSync.config)

        // Verify bitmap is not blank (contains both black and white pixels)
        var hasBlack = false
        var hasWhite = false
        for (y in 0 until bitmapSync.height step 8) {
            for (x in 0 until bitmapSync.width step 8) {
                val pixel = bitmapSync.getPixel(x, y)
                if (pixel == Color.BLACK) hasBlack = true
                if (pixel == Color.WHITE) hasWhite = true
            }
        }
        assertTrue("QR code should contain black pixels", hasBlack)
        assertTrue("QR code should contain white pixels", hasWhite)

        // Test asynchronous encoding
        val bitmapAsync = QrEncoder.encode(testContent, sizePx = 300)
        assertNotNull(bitmapAsync)
        assertEquals(300, bitmapAsync.width)
        assertEquals(300, bitmapAsync.height)
    }
}
