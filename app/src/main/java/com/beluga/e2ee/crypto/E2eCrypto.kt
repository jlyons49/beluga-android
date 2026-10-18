package com.beluga.e2ee.crypto

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardened v2 cryptographic engine for Beluga E2EE protocol.
 *
 * Resolves:
 *   - Issue #1: zlib Adler-32 trailing 0x00 hang (zero padding completely removed)
 *   - Issue #2: Zip bomb DoS (strict 64 KB decompression limit enforced)
 *   - Issue #3: Directional session keys (txKey/rxKey) & per-message ephemeral key derivation
 *   - Issue #4: Identity-bound handshake transcript signing and verification
 *   - Issue #6: AES-256-GCM authenticated additional data (AAD) & NIST-recommended 12-byte IVs
 *   - Issue #7: Elliptic curve point validation (checks curve membership and point at infinity)
 *   - Issue #22: Isolated BouncyCastle provider singleton (no global Security mutation)
 */
object E2eCrypto {

    const val CURVE = "secp384r1"
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val GCM_TAG_BITS = 128
    const val COMPRESSED_PUBLIC_KEY_BYTES = 49
    const val MAX_DECOMPRESSED_BYTES = 64 * 1024 // 64 KB decompression bomb safety limit

    private val bcProvider = BouncyCastleProvider()
    private val secureRandom = SecureRandom()

    data class EncryptedPayload(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val tag: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedPayload
            if (!ciphertext.contentEquals(other.ciphertext)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (!tag.contentEquals(other.tag)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ciphertext.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + tag.contentHashCode()
            return result
        }
    }

    data class DerivedSessionKeys(
        val sid: String,
        val txKey: ByteArray,
        val rxKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DerivedSessionKeys
            if (sid != other.sid) return false
            if (!txKey.contentEquals(other.txKey)) return false
            if (!rxKey.contentEquals(other.rxKey)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sid.hashCode()
            result = 31 * result + txKey.contentHashCode()
            result = 31 * result + rxKey.contentHashCode()
            return result
        }
    }

    // --- Key generation ---

    fun generateKeyPair(): KeyPair {
        val kg = KeyPairGenerator.getInstance("EC", bcProvider)
        kg.initialize(ECNamedCurveTable.getParameterSpec(CURVE), secureRandom)
        return kg.generateKeyPair()
    }

    // --- Public Key Serialization & Validation ---

    /**
     * Serializes an EC public key to 49-byte ANSI X9.62 compressed point format.
     */
    fun publicKeyToBytes(publicKey: PublicKey): ByteArray {
        val bcKey = publicKey as? org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
            ?: throw IllegalArgumentException("PublicKey is not a BouncyCastle BCECPublicKey")
        val encoded = bcKey.q.getEncoded(true)
        require(encoded.size == COMPRESSED_PUBLIC_KEY_BYTES) {
            "Expected 49 compressed public key bytes, got ${encoded.size}"
        }
        return encoded
    }

    /**
     * Deserializes and validates an EC public key from 49-byte compressed point format.
     * Enforces curve membership and rejects the point at infinity (resolving Issue #7).
     */
    fun bytesToPublicKey(bytes: ByteArray): PublicKey {
        require(bytes.size == COMPRESSED_PUBLIC_KEY_BYTES) {
            "Invalid public key length: ${bytes.size} (expected $COMPRESSED_PUBLIC_KEY_BYTES bytes)"
        }
        val prefix = bytes[0].toInt() and 0xFF
        require(prefix == 0x02 || prefix == 0x03) {
            "Invalid X9.62 compressed point prefix: 0x${prefix.toString(16)}"
        }
        val params = ECNamedCurveTable.getParameterSpec(CURVE)
        val point = try {
            params.curve.decodePoint(bytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed elliptic curve point encoding", e)
        }
        require(!point.isInfinity) { "Elliptic curve point at infinity is rejected" }
        require(point.isValid) { "Elliptic curve point does not lie on $CURVE" }

        val spec = ECPublicKeySpec(point, params)
        return KeyFactory.getInstance("EC", bcProvider).generatePublic(spec)
    }

    // --- Private Key Serialization ---

    fun privateKeyToBytes(privateKey: PrivateKey): ByteArray = privateKey.encoded

    fun bytesToPrivateKey(bytes: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC", bcProvider).generatePrivate(spec)
    }

    // --- Raw ECDH ---

    /**
     * Performs raw ECDH key agreement returning the unhashed 48-byte shared secret Z.
     */
    fun performRawEcdh(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH", bcProvider)
        ka.init(privateKey)
        ka.doPhase(peerPublicKey, true)
        return ka.generateSecret()
    }

    // --- Directional Session Material Derivation ---

    /**
     * Unsigned lexicographical comparison for byte arrays.
     */
    fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) return byteA - byteB
        }
        return a.size - b.size
    }

    /**
     * Derives directional session keys (txKey, rxKey) and shared session ID (sid).
     *
     * Orders identity and ephemeral keys deterministically using unsigned comparison
     * so both endpoints compute identical directional keys and matching sid.
     */
    fun deriveSessionMaterial(
        sharedSecret: ByteArray,
        ourIdentityPub: ByteArray,
        peerIdentityPub: ByteArray,
        ourEphemeralPub: ByteArray,
        peerEphemeralPub: ByteArray
    ): DerivedSessionKeys {
        val (pkMin, pkMax) = if (compareUnsigned(ourIdentityPub, peerIdentityPub) <= 0) {
            Pair(ourIdentityPub, peerIdentityPub)
        } else {
            Pair(peerIdentityPub, ourIdentityPub)
        }

        val (ephMin, ephMax) = if (compareUnsigned(ourEphemeralPub, peerEphemeralPub) <= 0) {
            Pair(ourEphemeralPub, peerEphemeralPub)
        } else {
            Pair(peerEphemeralPub, ourEphemeralPub)
        }

        val salt = ephMin + ephMax // 98 bytes
        val identityBinding = pkMin + pkMax // 98 bytes

        val sidBytes = HkdfSha256.derive(
            ikm = sharedSecret,
            salt = salt,
            info = "BELUGA-v2-SID".toByteArray(Charsets.UTF_8) + identityBinding,
            length = 8
        )
        val sid = Base85.encode(sidBytes)

        val keyLowToHigh = HkdfSha256.derive(
            ikm = sharedSecret,
            salt = salt,
            info = "BELUGA-v2-KEY-L2H".toByteArray(Charsets.UTF_8) + identityBinding,
            length = 32
        )

        val keyHighToLow = HkdfSha256.derive(
            ikm = sharedSecret,
            salt = salt,
            info = "BELUGA-v2-KEY-H2L".toByteArray(Charsets.UTF_8) + identityBinding,
            length = 32
        )

        val isLow = ourIdentityPub.contentEquals(pkMin)
        val txKey = if (isLow) keyLowToHigh else keyHighToLow
        val rxKey = if (isLow) keyHighToLow else keyLowToHigh

        return DerivedSessionKeys(sid = sid, txKey = txKey, rxKey = rxKey)
    }

    /**
     * Derives a unique 32-byte per-message encryption key from a session directional chain key.
     */
    fun deriveMessageKey(chainKey: ByteArray, iv: ByteArray, sid: String, seq: Long): ByteArray {
        val info = "BELUGA-v2-MSG|$sid|$seq".toByteArray(Charsets.UTF_8)
        return HkdfSha256.derive(ikm = chainKey, salt = iv, info = info, length = 32)
    }

    // --- Authenticated Encryption with Associated Data (AEAD) ---

    /**
     * Compresses plaintext (RFC 1950 zlib with NO padding), encrypts with AES-256-GCM,
     * and authenticates any provided AAD metadata.
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): EncryptedPayload {
        val compressed = zlibCompress(plaintext)
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        val output = cipher.doFinal(compressed)
        val ctLen = output.size - GCM_TAG_BYTES
        val ciphertext = output.copyOf(ctLen)
        val tag = output.copyOfRange(ctLen, output.size)
        return EncryptedPayload(ciphertext = ciphertext, iv = iv, tag = tag)
    }

    /**
     * Decrypts AES-256-GCM ciphertext verifying tag and AAD, then decompresses payload.
     * Enforces the 64 KB decompression bomb safety limit.
     */
    fun decrypt(
        ciphertext: ByteArray,
        iv: ByteArray,
        tag: ByteArray,
        key: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(iv.size == GCM_IV_BYTES) { "Invalid IV length: ${iv.size} (expected $GCM_IV_BYTES bytes)" }
        require(tag.size == GCM_TAG_BYTES) { "Invalid tag length: ${tag.size} (expected $GCM_TAG_BYTES bytes)" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        val compressed = cipher.doFinal(ciphertext + tag)
        return zlibDecompress(compressed)
    }

    // --- Handshake Transcript Signatures (Mode 3) ---

    fun buildHandshakeTranscript(
        signerId: String,
        signerIdentityPub: ByteArray,
        recipientIdentityPub: ByteArray,
        ephemeralPub: ByteArray,
        timestampSec: Long
    ): ByteArray {
        val prefix = "BELUGA-v2-MODE3".toByteArray(Charsets.UTF_8)
        val signerIdBytes = signerId.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        bos.write(prefix)
        val idLenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(signerIdBytes.size).array()
        bos.write(idLenBuf)
        bos.write(signerIdBytes)
        bos.write(signerIdentityPub)
        bos.write(recipientIdentityPub)
        bos.write(ephemeralPub)
        val tsBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timestampSec).array()
        bos.write(tsBuf)
        return bos.toByteArray()
    }

    fun signHandshake(
        signingKey: PrivateKey,
        signerId: String,
        signerIdentityPub: ByteArray,
        recipientIdentityPub: ByteArray,
        ephemeralPub: ByteArray,
        timestampSec: Long
    ): ByteArray {
        val transcript = buildHandshakeTranscript(
            signerId = signerId,
            signerIdentityPub = signerIdentityPub,
            recipientIdentityPub = recipientIdentityPub,
            ephemeralPub = ephemeralPub,
            timestampSec = timestampSec
        )
        val sig = Signature.getInstance("SHA256withECDSA", bcProvider)
        sig.initSign(signingKey)
        sig.update(transcript)
        return sig.sign()
    }

    fun verifyHandshake(
        signerPublicKey: PublicKey,
        signature: ByteArray,
        signerId: String,
        signerIdentityPub: ByteArray,
        recipientIdentityPub: ByteArray,
        ephemeralPub: ByteArray,
        timestampSec: Long
    ): Boolean {
        return try {
            val transcript = buildHandshakeTranscript(
                signerId = signerId,
                signerIdentityPub = signerIdentityPub,
                recipientIdentityPub = recipientIdentityPub,
                ephemeralPub = ephemeralPub,
                timestampSec = timestampSec
            )
            val sig = Signature.getInstance("SHA256withECDSA", bcProvider)
            sig.initVerify(signerPublicKey)
            sig.update(transcript)
            sig.verify(signature)
        } catch (e: SignatureException) {
            false
        }
    }

    // --- Safe RFC 1950 zlib compression / decompression ---

    fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(data)
        deflater.finish()
        val bos = ByteArrayOutputStream(data.size + 64)
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            bos.write(buf, 0, count)
        }
        deflater.end()
        return bos.toByteArray()
    }

    fun zlibDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater(false)
        inflater.setInput(data)
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                if (inflater.needsInput() || inflater.needsDictionary()) {
                    throw GeneralSecurityException("Truncated or incomplete zlib compressed stream")
                }
                val count = inflater.inflate(buf)
                if (count > 0) {
                    if (bos.size() + count > MAX_DECOMPRESSED_BYTES) {
                        throw GeneralSecurityException(
                            "Decompressed payload size exceeds $MAX_DECOMPRESSED_BYTES byte limit (zip bomb defense)"
                        )
                    }
                    bos.write(buf, 0, count)
                }
            }
            return bos.toByteArray()
        } catch (e: DataFormatException) {
            throw GeneralSecurityException("Invalid zlib compressed payload format", e)
        } finally {
            inflater.end()
        }
    }
}
