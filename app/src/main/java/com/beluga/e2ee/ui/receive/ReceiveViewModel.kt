package com.beluga.e2ee.ui.receive

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.FingerprintGenerator
import com.beluga.e2ee.protocol.DecryptedMessageResult
import com.beluga.e2ee.protocol.E2eSystem
import com.beluga.e2ee.protocol.HandshakeOutcome
import com.beluga.e2ee.protocol.QrMessageParser
import com.beluga.e2ee.protocol.model.QrMessage
import com.beluga.e2ee.qr.QrEncoder
import com.beluga.e2ee.qr.ZxingQrAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScanResultState {
    object Idle : ScanResultState()

    data class MessageDecrypted(
        val contactId: String,
        val seq: Long,
        val plaintext: String
    ) : ScanResultState()

    data class MultiChunkProgress(
        val contactId: String,
        val receivedCount: Int,
        val totalCount: Int,
        val receivedIndices: List<Int>,
        val isDuplicate: Boolean
    ) : ScanResultState()

    data class HandshakeCompleted(
        val contactId: String,
        val sid: String,
        val replyQrJson: String,
        val replyQrBitmap: Bitmap?,
        val weInitiated: Boolean
    ) : ScanResultState()

    data class ContactDiscovered(
        val rawMessage: QrMessage.SharePublicKey,
        val proposedName: String,
        val shortKeyId: String,
        val safetyNumber: String
    ) : ScanResultState()

    data class Error(val message: String) : ScanResultState()
}

class ReceiveViewModel(
    private val e2eSystem: E2eSystem
) : ViewModel() {

    private val _resultState = MutableStateFlow<ScanResultState>(ScanResultState.Idle)
    val resultState: StateFlow<ScanResultState> = _resultState.asStateFlow()

    private var lastScannedPayload: String? = null
    private var lastScannedTimestamp: Long = 0L

    fun processQrPayload(rawJson: String) {
        val now = System.currentTimeMillis()
        // Simple debounce for identical single-chunk scans within 800ms
        if (rawJson == lastScannedPayload && (now - lastScannedTimestamp) < 800) {
            return
        }
        lastScannedPayload = rawJson
        lastScannedTimestamp = now

        viewModelScope.launch {
            try {
                val message = QrMessageParser.parse(rawJson)
                when (message) {
                    is QrMessage.SingleChunk -> {
                        val decrypted = e2eSystem.decryptSingleChunk(message)
                        if (decrypted is DecryptedMessageResult.Decrypted) {
                            _resultState.value = ScanResultState.MessageDecrypted(
                                contactId = decrypted.contactId,
                                seq = decrypted.seq,
                                plaintext = decrypted.plaintext
                            )
                        }
                    }

                    is QrMessage.MultiChunk -> {
                        // Issue #8 fix: continuously process chunks without UI freeze
                        val chunkResult = e2eSystem.processMultiChunk(message)
                        when (chunkResult) {
                            is DecryptedMessageResult.ChunkProgress -> {
                                _resultState.value = ScanResultState.MultiChunkProgress(
                                    contactId = chunkResult.contactId,
                                    receivedCount = chunkResult.receivedCount,
                                    totalCount = chunkResult.totalCount,
                                    receivedIndices = chunkResult.receivedIndices,
                                    isDuplicate = chunkResult.isDuplicate
                                )
                            }
                            is DecryptedMessageResult.Decrypted -> {
                                _resultState.value = ScanResultState.MessageDecrypted(
                                    contactId = chunkResult.contactId,
                                    seq = chunkResult.seq,
                                    plaintext = chunkResult.plaintext
                                )
                            }
                        }
                    }

                    is QrMessage.SessionInit -> {
                        val outcome = e2eSystem.processSessionInit(message)
                        if (outcome is HandshakeOutcome.Completed) {
                            val replyBmp = if (!outcome.weInitiated) {
                                QrEncoder.encode(outcome.replyQrJson, 800)
                            } else null

                            _resultState.value = ScanResultState.HandshakeCompleted(
                                contactId = outcome.contact.userId,
                                sid = outcome.session.sid,
                                replyQrJson = outcome.replyQrJson,
                                replyQrBitmap = replyBmp,
                                weInitiated = outcome.weInitiated
                            )
                        }
                    }

                    is QrMessage.SharePublicKey -> {
                        val pubBytes = Base85.decode(message.publicKeyBase85)
                        val shortKeyId = FingerprintGenerator.generateShortKeyId(pubBytes)
                        val safetyNumber = FingerprintGenerator.generateSafetyNumber(pubBytes, multiline = true)

                        _resultState.value = ScanResultState.ContactDiscovered(
                            rawMessage = message,
                            proposedName = message.userId ?: "New Contact",
                            shortKeyId = shortKeyId,
                            safetyNumber = safetyNumber
                        )
                    }
                }
            } catch (e: Exception) {
                // If not currently assembling multi-chunk, show error
                if (_resultState.value !is ScanResultState.MultiChunkProgress) {
                    _resultState.value = ScanResultState.Error(e.message ?: "Failed to process QR code")
                }
            }
        }
    }

    fun processGalleryUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val result = ZxingQrAnalyzer.decodeFromUri(context, uri)
                result.onSuccess { payload ->
                    processQrPayload(payload)
                }.onFailure { e ->
                    _resultState.value = ScanResultState.Error(e.message ?: "Failed to decode QR from image")
                }
            }
        }
    }

    fun saveDiscoveredContact(customName: String, onSaved: () -> Unit) {
        val current = _resultState.value as? ScanResultState.ContactDiscovered ?: return
        viewModelScope.launch {
            try {
                e2eSystem.addOrUpdateContact(customName.trim(), current.rawMessage)
                _resultState.value = ScanResultState.Idle
                onSaved()
            } catch (e: Exception) {
                _resultState.value = ScanResultState.Error("Failed to save contact: ${e.message}")
            }
        }
    }

    fun dismissResult() {
        _resultState.value = ScanResultState.Idle
        lastScannedPayload = null
    }
}
