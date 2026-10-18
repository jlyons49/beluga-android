package com.beluga.e2ee.ui.session

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.HandshakeOutcome
import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.model.PendingHandshake
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.protocol.model.SessionRecord
import com.beluga.e2ee.qr.QrEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionHandshakeUiState(
    val userId: String = "",
    val selectedTab: Int = 0, // 0 = Show QR, 1 = Scan Peer QR
    val pendingHandshake: PendingHandshake? = null,
    val qrBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val completedSession: SessionRecord? = null,
    val replyQrJson: String? = null,
    val replyQrBitmap: Bitmap? = null,
    val weInitiated: Boolean = true,
    val errorMessage: String? = null
)

class SessionViewModel(
    private val e2eSystem: E2eSystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionHandshakeUiState())
    val uiState: StateFlow<SessionHandshakeUiState> = _uiState.asStateFlow()

    fun loadHandshake(userId: String) {
        if (_uiState.value.userId == userId && _uiState.value.pendingHandshake != null) {
            // Already loaded for this user (survives rotation)
            return
        }

        _uiState.value = SessionHandshakeUiState(userId = userId, isLoading = true)
        viewModelScope.launch {
            try {
                // Issue #16 fix: initiateOrResume with forceNew=false preserves ongoing handshake
                val pending = e2eSystem.initiateOrResumeHandshake(userId, forceNew = false)
                val bmp = QrEncoder.encode(pending.ownQrPayload, 800)
                _uiState.value = _uiState.value.copy(
                    pendingHandshake = pending,
                    qrBitmap = bmp,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load handshake: ${e.message}"
                )
            }
        }
    }

    fun regenerateHandshake() {
        val userId = _uiState.value.userId
        if (userId.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val pending = e2eSystem.initiateOrResumeHandshake(userId, forceNew = true)
                val bmp = QrEncoder.encode(pending.ownQrPayload, 800)
                _uiState.value = _uiState.value.copy(
                    pendingHandshake = pending,
                    qrBitmap = bmp,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to regenerate handshake: ${e.message}"
                )
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex, errorMessage = null)
    }

    fun processScannedPeerQr(qrJson: String) {
        val userId = _uiState.value.userId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val msg = QrMessageParser.parse(qrJson)
                if (msg !is QrMessage.SessionInit) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Scanned QR is not a valid Session Handshake (expected Mode 3)"
                    )
                    return@launch
                }

                val outcome = e2eSystem.processSessionInit(msg, explicitUserId = userId)
                when (outcome) {
                    is HandshakeOutcome.Completed -> {
                        val replyBmp = if (!outcome.weInitiated) {
                            QrEncoder.encode(outcome.replyQrJson, 800)
                        } else null

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isCompleted = true,
                            completedSession = outcome.session,
                            replyQrJson = outcome.replyQrJson,
                            replyQrBitmap = replyBmp,
                            weInitiated = outcome.weInitiated
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Handshake verification failed: ${e.message}"
                )
            }
        }
    }
}
