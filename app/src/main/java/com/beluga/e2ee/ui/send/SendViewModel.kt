package com.beluga.e2ee.ui.send

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.model.Contact
import com.beluga.e2ee.qr.QrEncoder
import com.beluga.e2ee.storage.DatabaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SendUiState(
    val selectedContactId: String? = null,
    val plaintext: String = "",
    val estimatedCompressedBytes: Int = 0,
    val estimatedChunkCount: Int = 1,
    val isEncrypting: Boolean = false,
    val generatedPayloads: List<String> = emptyList(),
    val chunkBitmaps: List<Bitmap> = emptyList(),
    val currentChunkIndex: Int = 0,
    val isAutoCycling: Boolean = false,
    val cycleSpeedMs: Long = 900L,
    val errorMessage: String? = null
)

class SendViewModel(
    private val e2eSystem: E2eSystem,
    private val repository: DatabaseRepository
) : ViewModel() {

    val activeContacts: StateFlow<List<Contact>> = combine(
        repository.contacts,
        repository.sessions
    ) { contactsList, sessionsMap ->
        contactsList.filter { sessionsMap.containsKey(it.userId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private var autoCycleJob: Job? = null

    fun selectContact(userId: String) {
        _uiState.value = _uiState.value.copy(selectedContactId = userId, errorMessage = null)
    }

    fun onPlaintextChanged(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val compressed = try {
            E2eCrypto.zlibCompress(bytes)
        } catch (_: Exception) {
            bytes
        }
        val compSize = compressed.size
        val chunks = if (compSize <= 400) 1 else (compSize + 399) / 400

        _uiState.value = _uiState.value.copy(
            plaintext = text,
            estimatedCompressedBytes = compSize,
            estimatedChunkCount = chunks,
            errorMessage = null
        )
    }

    fun encryptAndGenerateQrs(onSuccess: () -> Unit) {
        val state = _uiState.value
        val userId = state.selectedContactId
        if (userId.isNullOrEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please select a contact")
            return
        }
        if (state.plaintext.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please enter a message to send")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEncrypting = true, errorMessage = null)
            try {
                val payloads = e2eSystem.encryptMessage(userId, state.plaintext)
                val bitmaps = payloads.map { payload ->
                    QrEncoder.encode(payload, 800)
                }

                _uiState.value = _uiState.value.copy(
                    isEncrypting = false,
                    generatedPayloads = payloads,
                    chunkBitmaps = bitmaps,
                    currentChunkIndex = 0,
                    isAutoCycling = bitmaps.size > 1
                )

                if (bitmaps.size > 1) {
                    startAutoCycle()
                }

                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEncrypting = false,
                    errorMessage = "Encryption failed: ${e.message}"
                )
            }
        }
    }

    fun nextChunk() {
        val state = _uiState.value
        val total = state.chunkBitmaps.size
        if (total <= 1) return
        val next = (state.currentChunkIndex + 1) % total
        _uiState.value = state.copy(currentChunkIndex = next)
    }

    fun previousChunk() {
        val state = _uiState.value
        val total = state.chunkBitmaps.size
        if (total <= 1) return
        val prev = if (state.currentChunkIndex == 0) total - 1 else state.currentChunkIndex - 1
        _uiState.value = state.copy(currentChunkIndex = prev)
    }

    fun toggleAutoCycle() {
        val current = _uiState.value.isAutoCycling
        if (current) {
            stopAutoCycle()
        } else {
            startAutoCycle()
        }
    }

    fun setCycleSpeed(speedMs: Long) {
        _uiState.value = _uiState.value.copy(cycleSpeedMs = speedMs)
        if (_uiState.value.isAutoCycling) {
            startAutoCycle() // Restart with new speed
        }
    }

    private fun startAutoCycle() {
        autoCycleJob?.cancel()
        _uiState.value = _uiState.value.copy(isAutoCycling = true)
        autoCycleJob = viewModelScope.launch {
            while (isActive) {
                delay(_uiState.value.cycleSpeedMs)
                nextChunk()
            }
        }
    }

    private fun stopAutoCycle() {
        autoCycleJob?.cancel()
        autoCycleJob = null
        _uiState.value = _uiState.value.copy(isAutoCycling = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoCycle()
    }
}
