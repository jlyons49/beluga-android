package com.beluga.e2ee.ui.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beluga.e2ee.crypto.Base85
import com.beluga.e2ee.crypto.E2eCrypto
import com.beluga.e2ee.storage.DatabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordStrength(val label: String) {
    EMPTY(""),
    WEAK("Weak (minimum 8 characters)"),
    MEDIUM("Medium (good mix)"),
    STRONG("Strong (hardened)")
}

data class UnlockUiState(
    val isDatabaseCreated: Boolean = false,
    val isCheckingDb: Boolean = true,
    val displayName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.EMPTY,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UnlockViewModel(private val repository: DatabaseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        checkDatabaseStatus()
    }

    fun checkDatabaseStatus() {
        viewModelScope.launch {
            val exists = repository.isDatabaseCreated()
            _uiState.value = _uiState.value.copy(
                isDatabaseCreated = exists,
                isCheckingDb = false,
                errorMessage = null
            )
        }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        val strength = calculatePasswordStrength(password)
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordStrength = strength,
            errorMessage = null
        )
    }

    fun onConfirmPasswordChanged(confirm: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirm, errorMessage = null)
    }

    private fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        if (password.length < 8) return PasswordStrength.WEAK
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        val criteriaCount = listOf(hasUpper, hasLower, hasDigit, hasSpecial).count { it }
        return if (password.length >= 12 && criteriaCount >= 3) {
            PasswordStrength.STRONG
        } else if (criteriaCount >= 2) {
            PasswordStrength.MEDIUM
        } else {
            PasswordStrength.WEAK
        }
    }

    fun createVault(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.displayName.trim()
        val password = state.password

        if (name.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please enter a display name or nickname")
            return
        }
        if (password.length < 8) {
            _uiState.value = state.copy(errorMessage = "Master password must be at least 8 characters long")
            return
        }
        // Issue #17 fix: Enforce confirmation password validation
        if (password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match. Please re-enter confirmation.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Generate long-term P-384 identity keypair
                val keyPair = E2eCrypto.generateKeyPair()
                val signingKeyB85 = Base85.encode(E2eCrypto.privateKeyToBytes(keyPair.private))

                val success = repository.createDatabase(
                    password = password,
                    initialDisplayName = name,
                    initialSigningKey = signingKeyB85
                )

                if (success) {
                    _uiState.value = _uiState.value.copy(isLoading = false, password = "", confirmPassword = "")
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to initialize encrypted database"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error initializing vault: ${e.message}"
                )
            }
        }
    }

    fun unlockVault(onSuccess: () -> Unit) {
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your master password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val success = repository.unlock(password)
            if (success) {
                _uiState.value = _uiState.value.copy(isLoading = false, password = "")
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Incorrect password or corrupted vault file"
                )
            }
        }
    }
}
