package com.beluga.e2ee.protocol

import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.crypto.FingerprintGenerator
import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.protocol.model.PendingChunkState
import com.beluga.e2ee.protocol.model.PendingHandshake
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.protocol.model.SessionRecord
import com.beluga.e2ee.storage.DatabaseRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Outcome of processing an incoming or mutual Mode 3 SessionInit handshake packet.
 */
sealed class HandshakeOutcome {
    data class Completed(
        val contact: Contact,
        val session: SessionRecord,
        val replyQrJson: String,
        val weInitiated: Boolean
    ) : HandshakeOutcome()
}

/**
 * Result of decrypting a single-chunk (Mode 1) or processing a multi-chunk (Mode 2) QR code.
 */
sealed class DecryptedMessageResult {
    data class Decrypted(
        val contactId: String,
        val seq: Long,
        val plaintext: String
    ) : DecryptedMessageResult()

    data class ChunkProgress(
        val contactId: String,
        val receivedCount: Int,
        val totalCount: Int,
        val receivedIndices: List<Int>,
        val isDuplicate: Boolean
    ) : DecryptedMessageResult()
}

/**
 * Exception thrown when an incoming message sequence number is <= lastRxSeq.
 */
class ReplayAttackException(message: String) : SecurityException(message)

/**
 * Thread-safe end-to-end cryptographic protocol coordinator for Beluga v2.
 *
 * Implements:
 *   - Mode 6 long-term identity public key sharing and validation (Issue #7).
 *   - Mode 3 ephemeral authenticated session handshakes with transcript signatures (Issue #4).
 *   - Session handshake persistence surviving screen rotation & navigation (Issue #5 & #16).
 *   - Directional transmission and reception chain keys (Issue #3).
 *   - Per-message ephemeral key derivation with 12-byte random IVs and AES-256-GCM AAD (Issue #6).
 *   - Monotonic sequence numbers and strict replay protection.
 *   - Multi-chunk message slicing, out-of-order reassembly, and chunk deduplication (Issue #8).
 */
class E2eSystem(val repo: DatabaseRepository) {

    private val mutex = Mutex()
    private val secureRandom = SecureRandom()
    private val pendingChunks: MutableMap<String, PendingChunkState> = mutableMapOf()

    // --- Identity & Fingerprints ---

    fun getMyPublicKeyBytes(): ByteArray {
        val signingKeyB85 = repo.signingKey.value
            ?: throw IllegalStateException("Database is locked or signing key is missing")
        val privKey = E2eCrypto.bytesToPrivateKey(Base85.decode(signingKeyB85))
        val pubKey = E2eCrypto.privateKeyToPublicKey(privKey)
        return E2eCrypto.publicKeyToBytes(pubKey)
    }

    fun getMySafetyNumber(): String {
        return FingerprintGenerator.generateSafetyNumber(getMyPublicKeyBytes(), multiline = false)
    }

    fun getMyShortKeyId(): String {
        return FingerprintGenerator.generateShortKeyId(getMyPublicKeyBytes())
    }

    fun shareIdentityKey(): String {
        val name = repo.displayName.value ?: "Anonymous"
        val pkB85 = Base85.encode(getMyPublicKeyBytes())
        val msg = QrMessage.SharePublicKey(version = 2, userId = name, publicKeyBase85 = pkB85)
        return QrMessageParser.serialize(msg)
    }

    // --- Contact Management ---

    suspend fun addOrUpdateContact(userId: String, msg: QrMessage.SharePublicKey): Contact = mutex.withLock {
        val pkBytes = Base85.decode(msg.publicKeyBase85)
        // Validates length, compression prefix, curve membership, and rejects point at infinity (Issue #7)
        E2eCrypto.bytesToPublicKey(pkBytes)

        val existing = repo.getContact(userId)
        val contact = Contact(
            userId = userId,
            publicKeyBase85 = msg.publicKeyBase85,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            verified = existing?.verified ?: false
        )
        repo.saveContact(contact)
        contact
    }

    // --- Session Handshake (Mode 3) ---

    /**
     * Initiates or resumes a Mode 3 handshake for the specified contact.
     *
     * If a PendingHandshake already exists and [forceNew] is false, returns the existing
     * handshake so UI configuration changes do not invalidate displayed QR codes (Issue #16).
     */
    suspend fun initiateOrResumeHandshake(userId: String, forceNew: Boolean = false): PendingHandshake = mutex.withLock {
        if (!forceNew) {
            val existing = repo.getPendingHandshake(userId)
            if (existing != null) {
                return@withLock existing
            }
        }

        val contact = repo.getContact(userId)
            ?: throw IllegalArgumentException("Contact not found: $userId")
        val contactPubBytes = Base85.decode(contact.publicKeyBase85)
        val ourPubBytes = getMyPublicKeyBytes()
        val ourShortKeyId = getMyShortKeyId()
        val ourSigningKeyB85 = repo.signingKey.value
            ?: throw IllegalStateException("Database is locked or signing key is missing")
        val ourSigningKey = E2eCrypto.bytesToPrivateKey(Base85.decode(ourSigningKeyB85))

        val ephKeyPair = E2eCrypto.generateKeyPair()
        val ephPubBytes = E2eCrypto.publicKeyToBytes(ephKeyPair.public)
        val ephPrivBytes = E2eCrypto.privateKeyToBytes(ephKeyPair.private)
        val nowSec = System.currentTimeMillis() / 1000L

        val signature = E2eCrypto.signHandshake(
            signingKey = ourSigningKey,
            signerId = ourShortKeyId,
            signerIdentityPub = ourPubBytes,
            recipientIdentityPub = contactPubBytes,
            ephemeralPub = ephPubBytes,
            timestampSec = nowSec
        )

        val msg = QrMessage.SessionInit(
            version = 2,
            senderKeyId = ourShortKeyId,
            ephemeralPublicKeyBase85 = Base85.encode(ephPubBytes),
            timestampSec = nowSec,
            signatureBase85 = Base85.encode(signature)
        )
        val qrPayload = QrMessageParser.serialize(msg)

        val pending = PendingHandshake(
            userId = userId,
            ephemeralPrivateKeyBase85 = Base85.encode(ephPrivBytes),
            ephemeralPublicKeyBase85 = Base85.encode(ephPubBytes),
            ownQrPayload = qrPayload,
            createdAt = System.currentTimeMillis()
        )
        repo.savePendingHandshake(pending)
        pending
    }

    /**
     * Processes an incoming Mode 3 SessionInit message.
     *
     * Resolves the contact matching `msg.senderKeyId` (or uses `explicitUserId` if provided).
     * Validates ephemeral key on curve and verifies signature transcript.
     * Computes ECDH and directional session keys, persisting the session record.
     */
    suspend fun processSessionInit(
        msg: QrMessage.SessionInit,
        explicitUserId: String? = null
    ): HandshakeOutcome = mutex.withLock {
        val contact = if (explicitUserId != null) {
            repo.getContact(explicitUserId)
        } else {
            repo.contacts.value.find { c ->
                val cPubBytes = Base85.decode(c.publicKeyBase85)
                FingerprintGenerator.generateShortKeyId(cPubBytes) == msg.senderKeyId
            }
        } ?: throw IllegalArgumentException("No matching contact found for sender key ID '${msg.senderKeyId}'")

        val contactPubBytes = Base85.decode(contact.publicKeyBase85)
        val contactPubKey = E2eCrypto.bytesToPublicKey(contactPubBytes)
        val ourPubBytes = getMyPublicKeyBytes()

        val peerEphBytes = Base85.decode(msg.ephemeralPublicKeyBase85)
        val peerEphPubKey = E2eCrypto.bytesToPublicKey(peerEphBytes) // Validates on secp384r1

        val sigBytes = Base85.decode(msg.signatureBase85)
        val validSig = E2eCrypto.verifyHandshake(
            signerPublicKey = contactPubKey,
            signature = sigBytes,
            signerId = msg.senderKeyId,
            signerIdentityPub = contactPubBytes,
            recipientIdentityPub = ourPubBytes,
            ephemeralPub = peerEphBytes,
            timestampSec = msg.timestampSec
        )
        if (!validSig) {
            throw GeneralSecurityException("Invalid handshake signature from contact '${contact.userId}'")
        }

        val existingPending = repo.getPendingHandshake(contact.userId)
        if (existingPending != null) {
            // Case A: We already initiated / have a PendingHandshake for this contact
            val ourEphPriv = E2eCrypto.bytesToPrivateKey(Base85.decode(existingPending.ephemeralPrivateKeyBase85))
            val ourEphPubBytes = Base85.decode(existingPending.ephemeralPublicKeyBase85)
            val sharedSecret = E2eCrypto.performRawEcdh(ourEphPriv, peerEphPubKey)

            val derived = E2eCrypto.deriveSessionMaterial(
                sharedSecret = sharedSecret,
                ourIdentityPub = ourPubBytes,
                peerIdentityPub = contactPubBytes,
                ourEphemeralPub = ourEphPubBytes,
                peerEphemeralPub = peerEphBytes
            )

            val session = SessionRecord(
                userId = contact.userId,
                sid = derived.sid,
                txKeyBase85 = Base85.encode(derived.txKey),
                rxKeyBase85 = Base85.encode(derived.rxKey),
                txSeq = 0L,
                lastRxSeq = 0L,
                establishedAt = System.currentTimeMillis()
            )
            repo.saveSession(session)
            repo.deletePendingHandshake(contact.userId)

            HandshakeOutcome.Completed(
                contact = contact,
                session = session,
                replyQrJson = existingPending.ownQrPayload,
                weInitiated = true
            )
        } else {
            // Case B: Peer initiated & we did not have an existing PendingHandshake for this contact
            val ourSigningKeyB85 = repo.signingKey.value
                ?: throw IllegalStateException("Database is locked or signing key is missing")
            val ourSigningKey = E2eCrypto.bytesToPrivateKey(Base85.decode(ourSigningKeyB85))
            val ourShortKeyId = getMyShortKeyId()

            val ourEphKeyPair = E2eCrypto.generateKeyPair()
            val ourEphPubBytes = E2eCrypto.publicKeyToBytes(ourEphKeyPair.public)
            val nowSec = System.currentTimeMillis() / 1000L

            val replySig = E2eCrypto.signHandshake(
                signingKey = ourSigningKey,
                signerId = ourShortKeyId,
                signerIdentityPub = ourPubBytes,
                recipientIdentityPub = contactPubBytes,
                ephemeralPub = ourEphPubBytes,
                timestampSec = nowSec
            )

            val replyMsg = QrMessage.SessionInit(
                version = 2,
                senderKeyId = ourShortKeyId,
                ephemeralPublicKeyBase85 = Base85.encode(ourEphPubBytes),
                timestampSec = nowSec,
                signatureBase85 = Base85.encode(replySig)
            )
            val replyJson = QrMessageParser.serialize(replyMsg)

            val sharedSecret = E2eCrypto.performRawEcdh(ourEphKeyPair.private, peerEphPubKey)
            val derived = E2eCrypto.deriveSessionMaterial(
                sharedSecret = sharedSecret,
                ourIdentityPub = ourPubBytes,
                peerIdentityPub = contactPubBytes,
                ourEphemeralPub = ourEphPubBytes,
                peerEphemeralPub = peerEphBytes
            )

            val session = SessionRecord(
                userId = contact.userId,
                sid = derived.sid,
                txKeyBase85 = Base85.encode(derived.txKey),
                rxKeyBase85 = Base85.encode(derived.rxKey),
                txSeq = 0L,
                lastRxSeq = 0L,
                establishedAt = System.currentTimeMillis()
            )
            repo.saveSession(session)

            HandshakeOutcome.Completed(
                contact = contact,
                session = session,
                replyQrJson = replyJson,
                weInitiated = false
            )
        }
    }

    // --- Message Encryption (Modes 1 & 2) ---

    suspend fun encryptMessage(userId: String, plaintext: String): List<String> = mutex.withLock {
        val session = repo.getSession(userId)
            ?: throw IllegalArgumentException("No active session found with contact: $userId")
        val nextTxSeq = session.txSeq + 1
        val txKey = Base85.decode(session.txKeyBase85)

        val iv = ByteArray(E2eCrypto.GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val msgKey = E2eCrypto.deriveMessageKey(txKey, iv, session.sid, nextTxSeq)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val compressed = E2eCrypto.zlibCompress(plaintextBytes)
        val compressedSize = compressed.size

        val (mode, total) = if (compressedSize <= 400) {
            Pair(1, 1)
        } else {
            Pair(2, (compressedSize + 399) / 400)
        }

        val aad = "v2|$mode|${session.sid}|$nextTxSeq|$total".toByteArray(Charsets.UTF_8)
        val encrypted = E2eCrypto.encrypt(plaintext = plaintextBytes, key = msgKey, aad = aad, iv = iv)

        repo.updateTxSeq(userId, nextTxSeq)

        val ivB85 = Base85.encode(encrypted.iv)
        val tagB85 = Base85.encode(encrypted.tag)

        if (mode == 1) {
            val ctB85 = Base85.encode(encrypted.ciphertext)
            val msg = QrMessage.SingleChunk(
                version = 2,
                sid = session.sid,
                seq = nextTxSeq,
                ivBase85 = ivB85,
                ciphertextBase85 = ctB85,
                tagBase85 = tagB85
            )
            listOf(QrMessageParser.serialize(msg))
        } else {
            val result = ArrayList<String>(total)
            for (i in 0 until total) {
                val start = i * 400
                val end = minOf(start + 400, encrypted.ciphertext.size)
                val slice = encrypted.ciphertext.copyOfRange(start, end)
                val ctB85 = Base85.encode(slice)
                val msg = QrMessage.MultiChunk(
                    version = 2,
                    sid = session.sid,
                    seq = nextTxSeq,
                    index = i,
                    total = total,
                    ivBase85 = ivB85,
                    ciphertextBase85 = ctB85,
                    tagBase85 = tagB85
                )
                result.add(QrMessageParser.serialize(msg))
            }
            result
        }
    }

    // --- Message Decryption (Modes 1 & 2) ---

    suspend fun decryptSingleChunk(msg: QrMessage.SingleChunk): DecryptedMessageResult = mutex.withLock {
        val session = repo.getSessionBySid(msg.sid)
            ?: throw IllegalArgumentException("No active session found for session ID: ${msg.sid}")

        if (msg.seq <= session.lastRxSeq) {
            throw ReplayAttackException("Message #${msg.seq} has already been received (last seq: ${session.lastRxSeq})")
        }

        val rxKey = Base85.decode(session.rxKeyBase85)
        val iv = Base85.decode(msg.ivBase85)
        val tag = Base85.decode(msg.tagBase85)
        val ciphertext = Base85.decode(msg.ciphertextBase85)

        val msgKey = E2eCrypto.deriveMessageKey(rxKey, iv, session.sid, msg.seq)
        val aad = "v2|1|${session.sid}|${msg.seq}|1".toByteArray(Charsets.UTF_8)
        val decryptedBytes = E2eCrypto.decrypt(ciphertext, iv, tag, msgKey, aad)

        repo.updateLastRxSeq(session.userId, msg.seq)
        DecryptedMessageResult.Decrypted(
            contactId = session.userId,
            seq = msg.seq,
            plaintext = String(decryptedBytes, Charsets.UTF_8)
        )
    }

    suspend fun processMultiChunk(msg: QrMessage.MultiChunk): DecryptedMessageResult = mutex.withLock {
        val session = repo.getSessionBySid(msg.sid)
            ?: throw IllegalArgumentException("No active session found for session ID: ${msg.sid}")

        if (msg.seq <= session.lastRxSeq) {
            throw ReplayAttackException("Message #${msg.seq} has already been received (last seq: ${session.lastRxSeq})")
        }
        if (msg.total !in 2..64) {
            throw IllegalArgumentException("Invalid chunk total: ${msg.total} (expected 2..64)")
        }
        if (msg.index !in 0 until msg.total) {
            throw IllegalArgumentException("Invalid chunk index: ${msg.index} (expected 0 until ${msg.total})")
        }

        val key = "${msg.sid}:${msg.seq}"
        val state = pendingChunks.getOrPut(key) {
            PendingChunkState(
                sid = msg.sid,
                seq = msg.seq,
                total = msg.total,
                iv = msg.ivBase85,
                tag = msg.tagBase85
            )
        }

        val isDuplicate = state.chunks.containsKey(msg.index)
        if (!isDuplicate) {
            state.chunks[msg.index] = msg.ciphertextBase85
        }

        if (!state.isComplete) {
            return@withLock DecryptedMessageResult.ChunkProgress(
                contactId = session.userId,
                receivedCount = state.chunks.size,
                totalCount = state.total,
                receivedIndices = state.chunks.keys.sorted(),
                isDuplicate = isDuplicate
            )
        }

        // All chunks received: reassemble in order
        val rxKey = Base85.decode(session.rxKeyBase85)
        val iv = Base85.decode(state.iv)
        val tag = Base85.decode(state.tag)

        val bos = ByteArrayOutputStream()
        for (i in 0 until state.total) {
            val sliceB85 = state.chunks[i] ?: error("Missing chunk slice $i in complete state")
            bos.write(Base85.decode(sliceB85))
        }
        val fullCiphertext = bos.toByteArray()

        val msgKey = E2eCrypto.deriveMessageKey(rxKey, iv, session.sid, msg.seq)
        val aad = "v2|2|${session.sid}|${msg.seq}|${msg.total}".toByteArray(Charsets.UTF_8)
        val decryptedBytes = E2eCrypto.decrypt(fullCiphertext, iv, tag, msgKey, aad)

        repo.updateLastRxSeq(session.userId, msg.seq)
        pendingChunks.remove(key)

        DecryptedMessageResult.Decrypted(
            contactId = session.userId,
            seq = msg.seq,
            plaintext = String(decryptedBytes, Charsets.UTF_8)
        )
    }

    fun getPendingChunks(): Map<String, PendingChunkState> = pendingChunks
}
