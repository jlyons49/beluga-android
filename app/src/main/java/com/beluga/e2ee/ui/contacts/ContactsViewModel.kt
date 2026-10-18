package com.beluga.e2ee.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.FingerprintGenerator
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.protocol.model.SessionRecord
import com.beluga.e2ee.storage.DatabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactItemUi(
    val contact: Contact,
    val shortKeyId: String,
    val safetyNumber: String,
    val session: SessionRecord?
)

data class AddContactUiState(
    val scannedMessage: QrMessage.SharePublicKey? = null,
    val nameInput: String = "",
    val shortKeyId: String = "",
    val safetyNumber: String = "",
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

class ContactsViewModel(
    private val e2eSystem: E2eSystem,
    private val repository: DatabaseRepository
) : ViewModel() {

    val contactItems: StateFlow<List<ContactItemUi>> = combine(
        repository.contacts,
        repository.sessions
    ) { contactsList, sessionsMap ->
        contactsList.map { contact ->
            val pubBytes = try {
                Base85.decode(contact.publicKeyBase85)
            } catch (_: Exception) {
                ByteArray(0)
            }
            val shortKeyId = if (pubBytes.isNotEmpty()) FingerprintGenerator.generateShortKeyId(pubBytes) else "----"
            val safetyNumber = if (pubBytes.isNotEmpty()) FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = false) else ""
            val session = sessionsMap[contact.userId]
            ContactItemUi(
                contact = contact,
                shortKeyId = shortKeyId,
                safetyNumber = safetyNumber,
                session = session
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addContactState = MutableStateFlow(AddContactUiState())
    val addContactState: StateFlow<AddContactUiState> = _addContactState.asStateFlow()

    fun toggleVerified(userId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.setContactVerified(userId, !currentStatus)
        }
    }

    fun deleteContact(userId: String) {
        viewModelScope.launch {
            repository.deleteContact(userId)
        }
    }

    fun onScanMode6Qr(rawPayload: String) {
        try {
            val msg = QrMessageParser.parse(rawPayload)
            if (msg !is QrMessage.SharePublicKey) {
                _addContactState.value = _addContactState.value.copy(
                    errorMessage = "Scanned QR is not a Public Key (expected Mode 6)"
                )
                return
            }

            val pubBytes = Base85.decode(msg.publicKeyBase85)
            val shortKeyId = FingerprintGenerator.generateShortKeyId(pubBytes)
            val safetyNumber = FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = true)

            _addContactState.value = AddContactUiState(
                scannedMessage = msg,
                nameInput = msg.userId ?: "",
                shortKeyId = shortKeyId,
                safetyNumber = safetyNumber,
                errorMessage = null
            )
        } catch (e: Exception) {
            _addContactState.value = _addContactState.value.copy(
                errorMessage = "Failed to parse public key: ${e.message}"
            )
        }
    }

    fun onNameInputChanged(name: String) {
        _addContactState.value = _addContactState.value.copy(nameInput = name, errorMessage = null)
    }

    fun saveContact(onSuccess: () -> Unit) {
        val state = _addContactState.value
        val msg = state.scannedMessage ?: return
        val name = state.nameInput.trim()

        if (name.isEmpty()) {
            _addContactState.value = state.copy(errorMessage = "Please enter a name for this contact")
            return
        }

        viewModelScope.launch {
            try {
                e2eSystem.addOrUpdateContact(name, msg)
                _addContactState.value = state.copy(isSaved = true)
                onSuccess()
            } catch (e: Exception) {
                _addContactState.value = state.copy(errorMessage = "Failed to save contact: ${e.message}")
            }
        }
    }

    fun resetAddContactState() {
        _addContactState.value = AddContactUiState()
    }
}
