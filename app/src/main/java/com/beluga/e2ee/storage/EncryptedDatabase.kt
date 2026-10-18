package com.beluga.e2ee.storage

import android.content.Context
import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.HkdfSha256
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Self-contained on-disk encrypted JSON database engine (resolving Issues #13 and #14).
 *
 * Security & Reliability architecture:
 *   - Atomic file transactions via temporary files, fsync, and atomic swap to prevent database loss.
 *   - Automatic recovery from backup (.bak) file in the event of an abrupt power loss mid-transaction.
 *   - PBKDF2WithHmacSHA256 (300,000 iterations) + HKDF-SHA256 key derivation.
 *   - 32-byte per-database random salt stored in the outer JSON envelope (no deprecated security-crypto prefs).
 *   - AES-256-GCM authenticated encryption with 12-byte IV, 128-bit tag, and AAD header binding.
 */
class EncryptedDatabase(val dbFile: File) {

    constructor(context: Context) : this(File(context.filesDir, DB_FILE_NAME))

    companion object {
        const val DB_FILE_NAME = "database.bin"
        const val PBKDF2_ITERATIONS = 300_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        val DB_AAD: ByteArray = "v2|beluga-db".toByteArray(Charsets.UTF_8)

        /**
         * Writes bytes to file atomically with temporary staging file and fsync.
         */
        fun writeAtomic(file: File, bytes: ByteArray) {
            val parent = file.parentFile ?: File(".")
            if (!parent.exists()) parent.mkdirs()

            val tmpFile = File(parent, "${file.name}.tmp")
            val bakFile = File(parent, "${file.name}.bak")

            // 1. Stage into .tmp file and sync to disk
            FileOutputStream(tmpFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {
                    // Ignored on file systems where sync is unsupported
                }
            }

            // 2. Prepare backup of current file
            if (file.exists()) {
                if (bakFile.exists()) {
                    bakFile.delete()
                }
                if (!file.renameTo(bakFile)) {
                    file.copyTo(bakFile, overwrite = true)
                    file.delete()
                }
            }

            // 3. Move staged file to target destination
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }

            // 4. Remove backup upon confirmed commit
            if (bakFile.exists()) {
                bakFile.delete()
            }
        }

        /**
         * Reads bytes atomically, restoring from backup (.bak) file if an interrupted write is detected.
         */
        fun readAtomic(file: File): ByteArray {
            val parent = file.parentFile ?: File(".")
            val bakFile = File(parent, "${file.name}.bak")
            if (bakFile.exists() && (!file.exists() || file.length() == 0L)) {
                bakFile.renameTo(file)
            }
            return file.readBytes()
        }
    }

    private val secureRandom = SecureRandom()

    fun exists(): Boolean {
        val parent = dbFile.parentFile ?: File(".")
        val bakFile = File(parent, "${dbFile.name}.bak")
        return (dbFile.exists() && dbFile.length() > 0) || (bakFile.exists() && bakFile.length() > 0)
    }

    /**
     * Derives a 32-byte database encryption key using PBKDF2-HMAC-SHA256 (300,000 iterations) + HKDF-SHA256.
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val pbkdf2Out = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return HkdfSha256.derive(
            ikm = pbkdf2Out,
            salt = null,
            info = "BELUGA-DB-KEY".toByteArray(Charsets.UTF_8),
            length = 32
        )
    }

    /**
     * Creates and initializes a new encrypted database file with a fresh random 32-byte salt.
     *
     * @return Pair of (derived 32-byte key, salt)
     */
    fun create(password: String, initialJson: JSONObject): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveKey(password, salt)
        save(key = key, salt = salt, innerJson = initialJson)
        return Pair(key, salt)
    }

    /**
     * Decrypts and loads the inner database JSON from disk.
     * Throws AEADBadTagException or GeneralSecurityException if password or tag is incorrect.
     *
     * @return Triple of (derived 32-byte key, salt, decrypted JSONObject)
     */
    fun load(password: String): Triple<ByteArray, ByteArray, JSONObject> {
        if (!exists()) {
            throw FileNotFoundException("Database file does not exist at ${dbFile.absolutePath}")
        }
        val outerBytes = readAtomic(dbFile)
        val outerJson = JSONObject(outerBytes.toString(Charsets.UTF_8))

        val version = outerJson.optInt("v", 1)
        require(version == 2) { "Unsupported database version: $version" }

        val salt = Base85.decode(outerJson.getString("salt"))
        val iv = Base85.decode(outerJson.getString("iv"))
        val tag = Base85.decode(outerJson.getString("tag"))
        val ct = Base85.decode(outerJson.getString("ct"))

        require(iv.size == GCM_IV_BYTES) { "Invalid DB IV length: ${iv.size}" }
        require(tag.size == GCM_TAG_BYTES) { "Invalid DB tag length: ${tag.size}" }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(DB_AAD)
        val plaintext = cipher.doFinal(ct + tag)
        val innerJson = JSONObject(plaintext.toString(Charsets.UTF_8))

        return Triple(key, salt, innerJson)
    }

    /**
     * Atomically encrypts and writes the inner JSON database to disk.
     */
    fun save(key: ByteArray, salt: ByteArray, innerJson: JSONObject) {
        val plaintext = innerJson.toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(DB_AAD)
        val output = cipher.doFinal(plaintext)

        val ctLen = output.size - GCM_TAG_BYTES
        val ct = output.copyOf(ctLen)
        val tag = output.copyOfRange(ctLen, output.size)

        val outer = JSONObject().apply {
            put("v", 2)
            put("kdf", "PBKDF2-HMAC-SHA256-300000+HKDF-SHA256")
            put("salt", Base85.encode(salt))
            put("iv", Base85.encode(iv))
            put("tag", Base85.encode(tag))
            put("ct", Base85.encode(ct))
        }

        writeAtomic(dbFile, outer.toString().toByteArray(Charsets.UTF_8))
    }
}
