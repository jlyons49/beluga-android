package com.beluga.e2ee.storage

import android.content.Context
import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.protocol.model.PendingHandshake
import com.beluga.e2ee.protocol.model.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe repository managing the encrypted JSON database (resolving Issue #15).
 *
 * All state mutations and disk writes are protected by a Kotlin Mutex.
 * In-memory state is exposed via read-only StateFlows for seamless Compose UI reactivity.
 */
class DatabaseRepository(val encryptedDb: EncryptedDatabase) {

    constructor(context: Context) : this(EncryptedDatabase(context))
    constructor(dbFile: File) : this(EncryptedDatabase(dbFile))

    private val mutex = Mutex()

    private var activeKey: ByteArray? = null
    private var activeSalt: ByteArray? = null

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _signingKey = MutableStateFlow<String?>(null)
    val signingKey: StateFlow<String?> = _signingKey.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, SessionRecord>>(emptyMap())
    val sessions: StateFlow<Map<String, SessionRecord>> = _sessions.asStateFlow()

    private val _pendingHandshakes = MutableStateFlow<Map<String, PendingHandshake>>(emptyMap())
    val pendingHandshakes: StateFlow<Map<String, PendingHandshake>> = _pendingHandshakes.asStateFlow()

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        encryptedDb.exists()
    }

    /**
     * Initializes a brand-new database with the given master password.
     */
    suspend fun createDatabase(
        password: String,
        initialDisplayName: String? = null,
        initialSigningKey: String? = null
    ): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val initialJson = JSONObject().apply {
                    put("version", 2)
                    if (!initialDisplayName.isNullOrBlank()) {
                        put("displayName", initialDisplayName)
                    }
                    if (!initialSigningKey.isNullOrBlank()) {
                        put("signingKey", initialSigningKey)
                    }
                    put("contacts", JSONObject())
                    put("sessions", JSONObject())
                    put("pendingHandshakes", JSONObject())
                }
                val (key, salt) = encryptedDb.create(password, initialJson)
                activeKey = key
                activeSalt = salt

                _displayName.value = initialDisplayName
                _signingKey.value = initialSigningKey
                _contacts.value = emptyList()
                _sessions.value = emptyMap()
                _pendingHandshakes.value = emptyMap()
                _isUnlocked.value = true
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Unlocks an existing database with the master password.
     * Returns true on success, false on wrong password (AEADBadTagException) or corrupted file.
     */
    suspend fun unlock(password: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val (key, salt, innerJson) = encryptedDb.load(password)
                activeKey = key
                activeSalt = salt

                parseInnerJson(innerJson)
                _isUnlocked.value = true
                true
            } catch (e: Exception) {
                lockInternal()
                false
            }
        }
    }

    /**
     * Locks the database, zeroizes in-memory cryptographic keys, and resets state.
     */
    suspend fun lock() = mutex.withLock {
        lockInternal()
    }

    private fun lockInternal() {
        activeKey?.fill(0)
        activeKey = null
        activeSalt = null

        _displayName.value = null
        _signingKey.value = null
        _contacts.value = emptyList()
        _sessions.value = emptyMap()
        _pendingHandshakes.value = emptyMap()
        _isUnlocked.value = false
    }

    // --- Profile & Identity Key ---

    suspend fun setDisplayName(name: String?) = mutex.withLock {
        checkUnlocked()
        _displayName.value = name
        flushInternal()
    }

    suspend fun setSigningKey(base85Pkcs8: String) = mutex.withLock {
        checkUnlocked()
        _signingKey.value = base85Pkcs8
        flushInternal()
    }

    // --- Contacts ---

    suspend fun saveContact(contact: Contact) = mutex.withLock {
        checkUnlocked()
        val current = _contacts.value.toMutableList()
        val index = current.indexOfFirst { it.userId == contact.userId }
        if (index >= 0) {
            current[index] = contact
        } else {
            current.add(contact)
        }
        _contacts.value = current
        flushInternal()
    }

    suspend fun getContact(userId: String): Contact? = mutex.withLock {
        checkUnlocked()
        _contacts.value.find { it.userId == userId }
    }

    suspend fun deleteContact(userId: String) = mutex.withLock {
        checkUnlocked()
        _contacts.value = _contacts.value.filterNot { it.userId == userId }
        // Also remove associated session and pending handshakes
        if (_sessions.value.containsKey(userId)) {
            val sessionsMap = _sessions.value.toMutableMap()
            sessionsMap.remove(userId)
            _sessions.value = sessionsMap
        }
        if (_pendingHandshakes.value.containsKey(userId)) {
            val pendingMap = _pendingHandshakes.value.toMutableMap()
            pendingMap.remove(userId)
            _pendingHandshakes.value = pendingMap
        }
        flushInternal()
    }

    suspend fun setContactVerified(userId: String, verified: Boolean) = mutex.withLock {
        checkUnlocked()
        val current = _contacts.value.toMutableList()
        val index = current.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            current[index] = current[index].copy(verified = verified)
            _contacts.value = current
            flushInternal()
        }
    }

    // --- Sessions ---

    suspend fun saveSession(session: SessionRecord) = mutex.withLock {
        checkUnlocked()
        val current = _sessions.value.toMutableMap()
        current[session.userId] = session
        _sessions.value = current
        flushInternal()
    }

    suspend fun getSession(userId: String): SessionRecord? = mutex.withLock {
        checkUnlocked()
        _sessions.value[userId]
    }

    suspend fun getSessionBySid(sid: String): SessionRecord? = mutex.withLock {
        checkUnlocked()
        _sessions.value.values.find { it.sid == sid }
    }

    suspend fun deleteSession(userId: String) = mutex.withLock {
        checkUnlocked()
        val current = _sessions.value.toMutableMap()
        current.remove(userId)
        _sessions.value = current
        flushInternal()
    }

    suspend fun updateTxSeq(userId: String, newSeq: Long) = mutex.withLock {
        checkUnlocked()
        val current = _sessions.value.toMutableMap()
        val record = current[userId] ?: return@withLock
        current[userId] = record.copy(txSeq = newSeq)
        _sessions.value = current
        flushInternal()
    }

    suspend fun updateLastRxSeq(userId: String, newSeq: Long) = mutex.withLock {
        checkUnlocked()
        val current = _sessions.value.toMutableMap()
        val record = current[userId] ?: return@withLock
        current[userId] = record.copy(lastRxSeq = newSeq)
        _sessions.value = current
        flushInternal()
    }

    // --- Pending Handshakes ---

    suspend fun savePendingHandshake(pending: PendingHandshake) = mutex.withLock {
        checkUnlocked()
        val current = _pendingHandshakes.value.toMutableMap()
        current[pending.userId] = pending
        _pendingHandshakes.value = current
        flushInternal()
    }

    suspend fun getPendingHandshake(userId: String): PendingHandshake? = mutex.withLock {
        checkUnlocked()
        _pendingHandshakes.value[userId]
    }

    suspend fun deletePendingHandshake(userId: String) = mutex.withLock {
        checkUnlocked()
        val current = _pendingHandshakes.value.toMutableMap()
        current.remove(userId)
        _pendingHandshakes.value = current
        flushInternal()
    }

    // --- Internal Helpers ---

    private fun checkUnlocked() {
        check(_isUnlocked.value && activeKey != null && activeSalt != null) {
            "Database is locked"
        }
    }

    private fun parseInnerJson(json: JSONObject) {
        _displayName.value = json.optString("displayName", "").takeIf { it.isNotEmpty() }
        _signingKey.value = json.optString("signingKey", "").takeIf { it.isNotEmpty() }

        val contactsList = mutableListOf<Contact>()
        val contactsObj = json.optJSONObject("contacts") ?: JSONObject()
        val contactKeys = contactsObj.keys()
        while (contactKeys.hasNext()) {
            val userId = contactKeys.next()
            val c = contactsObj.getJSONObject(userId)
            contactsList.add(
                Contact(
                    userId = userId,
                    publicKeyBase85 = c.getString("publicKey"),
                    addedAt = c.optLong("addedAt", System.currentTimeMillis()),
                    verified = c.optBoolean("verified", false)
                )
            )
        }
        _contacts.value = contactsList

        val sessionsMap = mutableMapOf<String, SessionRecord>()
        val sessionsObj = json.optJSONObject("sessions") ?: JSONObject()
        val sessionKeys = sessionsObj.keys()
        while (sessionKeys.hasNext()) {
            val userId = sessionKeys.next()
            val s = sessionsObj.getJSONObject(userId)
            sessionsMap[userId] = SessionRecord(
                userId = userId,
                sid = s.getString("sid"),
                txKeyBase85 = s.getString("txKey"),
                rxKeyBase85 = s.getString("rxKey"),
                txSeq = s.optLong("txSeq", 1L),
                lastRxSeq = s.optLong("lastRxSeq", 0L),
                establishedAt = s.optLong("establishedAt", System.currentTimeMillis())
            )
        }
        _sessions.value = sessionsMap

        val pendingMap = mutableMapOf<String, PendingHandshake>()
        val pendingObj = json.optJSONObject("pendingHandshakes") ?: JSONObject()
        val pendingKeys = pendingObj.keys()
        while (pendingKeys.hasNext()) {
            val userId = pendingKeys.next()
            val p = pendingObj.getJSONObject(userId)
            pendingMap[userId] = PendingHandshake(
                userId = userId,
                ephemeralPrivateKeyBase85 = p.getString("ephemeralPrivateKey"),
                ephemeralPublicKeyBase85 = p.getString("ephemeralPublicKey"),
                ownQrPayload = p.getString("ownQrPayload"),
                createdAt = p.optLong("createdAt", System.currentTimeMillis())
            )
        }
        _pendingHandshakes.value = pendingMap
    }

    private fun serializeInnerJson(): JSONObject {
        return JSONObject().apply {
            put("version", 2)
            _displayName.value?.let { put("displayName", it) }
            _signingKey.value?.let { put("signingKey", it) }

            val contactsObj = JSONObject()
            _contacts.value.forEach { c ->
                val cJson = JSONObject().apply {
                    put("publicKey", c.publicKeyBase85)
                    put("addedAt", c.addedAt)
                    put("verified", c.verified)
                }
                contactsObj.put(c.userId, cJson)
            }
            put("contacts", contactsObj)

            val sessionsObj = JSONObject()
            _sessions.value.forEach { (userId, s) ->
                val sJson = JSONObject().apply {
                    put("sid", s.sid)
                    put("txKey", s.txKeyBase85)
                    put("rxKey", s.rxKeyBase85)
                    put("txSeq", s.txSeq)
                    put("lastRxSeq", s.lastRxSeq)
                    put("establishedAt", s.establishedAt)
                }
                sessionsObj.put(userId, sJson)
            }
            put("sessions", sessionsObj)

            val pendingObj = JSONObject()
            _pendingHandshakes.value.forEach { (userId, p) ->
                val pJson = JSONObject().apply {
                    put("ephemeralPrivateKey", p.ephemeralPrivateKeyBase85)
                    put("ephemeralPublicKey", p.ephemeralPublicKeyBase85)
                    put("ownQrPayload", p.ownQrPayload)
                    put("createdAt", p.createdAt)
                }
                pendingObj.put(userId, pJson)
            }
            put("pendingHandshakes", pendingObj)
        }
    }

    private fun flushInternal() {
        val key = activeKey ?: error("Cannot flush database while locked")
        val salt = activeSalt ?: error("Cannot flush database without salt")
        val innerJson = serializeInnerJson()
        encryptedDb.save(key = key, salt = salt, innerJson = innerJson)
    }
}
