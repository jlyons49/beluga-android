package com.beluga.e2ee

import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.protocol.model.PendingHandshake
import com.beluga.e2ee.protocol.model.SessionRecord
import com.beluga.e2ee.storage.DatabaseRepository
import com.beluga.e2ee.storage.EncryptedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCreateAndUnlockFlow() = runBlocking {
        val dbFile = tempFolder.newFile("test_db.bin")
        dbFile.delete() // Ensure it starts non-existent

        val repo = DatabaseRepository(EncryptedDatabase(dbFile))
        assertFalse(repo.exists())
        assertFalse(repo.isUnlocked.value)

        // Create
        val created = repo.createDatabase(
            password = "correct_master_password_123",
            initialDisplayName = "Alice",
            initialSigningKey = "test_signing_key_b85"
        )
        assertTrue(created)
        assertTrue(repo.isUnlocked.value)
        assertEquals("Alice", repo.displayName.value)
        assertEquals("test_signing_key_b85", repo.signingKey.value)

        // Lock
        repo.lock()
        assertFalse(repo.isUnlocked.value)
        assertNull(repo.displayName.value)

        // Unlock with wrong password
        val wrongUnlocked = repo.unlock("wrong_password")
        assertFalse(wrongUnlocked)
        assertFalse(repo.isUnlocked.value)

        // Unlock with right password
        val rightUnlocked = repo.unlock("correct_master_password_123")
        assertTrue(rightUnlocked)
        assertTrue(repo.isUnlocked.value)
        assertEquals("Alice", repo.displayName.value)
    }

    @Test
    fun testContactsAndSessionsPersistence() = runBlocking {
        val dbFile = tempFolder.newFile("test_persistence.bin")
        dbFile.delete()

        val repo1 = DatabaseRepository(EncryptedDatabase(dbFile))
        repo1.createDatabase("password_abc", "Alice")

        val contactBob = Contact(
            userId = "Bob",
            publicKeyBase85 = "pubkey_bob_b85",
            addedAt = 1000L,
            verified = false
        )
        repo1.saveContact(contactBob)

        val sessionBob = SessionRecord(
            userId = "Bob",
            sid = "sid_12345",
            txKeyBase85 = "tx_key_b85",
            rxKeyBase85 = "rx_key_b85",
            txSeq = 5L,
            lastRxSeq = 3L,
            establishedAt = 2000L
        )
        repo1.saveSession(sessionBob)

        val pendingBob = PendingHandshake(
            userId = "Bob",
            ephemeralPrivateKeyBase85 = "eph_priv_b85",
            ephemeralPublicKeyBase85 = "eph_pub_b85",
            ownQrPayload = "{\"v\":2}",
            createdAt = 3000L
        )
        repo1.savePendingHandshake(pendingBob)

        repo1.lock()

        // Create completely new repo instance reading the same physical file
        val repo2 = DatabaseRepository(EncryptedDatabase(dbFile))
        assertTrue(repo2.exists())
        val unlocked = repo2.unlock("password_abc")
        assertTrue(unlocked)

        // Verify contact was restored
        val loadedContact = repo2.getContact("Bob")
        assertNotNull(loadedContact)
        assertEquals("Bob", loadedContact?.userId)
        assertEquals("pubkey_bob_b85", loadedContact?.publicKeyBase85)
        assertEquals(false, loadedContact?.verified)

        // Verify session was restored
        val loadedSession = repo2.getSession("Bob")
        assertNotNull(loadedSession)
        assertEquals("sid_12345", loadedSession?.sid)
        assertEquals(5L, loadedSession?.txSeq)
        assertEquals(3L, loadedSession?.lastRxSeq)

        // Verify session lookup by SID
        val sessionBySid = repo2.getSessionBySid("sid_12345")
        assertEquals(loadedSession, sessionBySid)

        // Verify pending handshake was restored
        val loadedPending = repo2.getPendingHandshake("Bob")
        assertNotNull(loadedPending)
        assertEquals("eph_priv_b85", loadedPending?.ephemeralPrivateKeyBase85)

        // Test update sequences
        repo2.updateTxSeq("Bob", 6L)
        repo2.updateLastRxSeq("Bob", 4L)
        assertEquals(6L, repo2.getSession("Bob")?.txSeq)
        assertEquals(4L, repo2.getSession("Bob")?.lastRxSeq)

        // Test contact deletion cascades to session and pending handshakes
        repo2.deleteContact("Bob")
        assertNull(repo2.getContact("Bob"))
        assertNull(repo2.getSession("Bob"))
        assertNull(repo2.getPendingHandshake("Bob"))
    }
}
