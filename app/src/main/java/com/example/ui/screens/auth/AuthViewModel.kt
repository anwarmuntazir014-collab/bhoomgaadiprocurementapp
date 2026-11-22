package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = true,
    val needsInitialAdminSetup: Boolean = false,
    val currentUser: UserEntity? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

class AuthViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _language = MutableStateFlow(repository.prefs.appLanguage)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeUsers: StateFlow<List<UserEntity>> = repository.observeActiveUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshAdminSetupState()
    }

    fun refreshAdminSetupState() {
        viewModelScope.launch {
            val hasAdmin = repository.hasAnyAdmin()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    needsInitialAdminSetup = !hasAdmin
                )
            }
        }
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
        repository.prefs.appLanguage = next
        _language.value = next
    }

    fun setLanguage(lang: AppLanguage) {
        repository.prefs.appLanguage = lang
        _language.value = lang
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun createInitialAdmin(username: String, displayName: String, pin: String, confirmPin: String) {
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _uiState.update { it.copy(errorMessage = "PIN must be exactly 4 digits.") }
            return
        }
        if (pin != confirmPin) {
            _uiState.update { it.copy(errorMessage = "PIN and Confirm PIN do not match.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repository.createInitialAdmin(username, displayName, pin)
            result.fold(
                onSuccess = { admin ->
                    repository.prefs.operatorName = admin.displayName
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            needsInitialAdminSetup = false,
                            currentUser = admin,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = err.message ?: "Unable to create Admin account.")
                    }
                }
            )
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repository.authenticateUser(username, pin)
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            currentUser = user,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = err.message ?: "Login failed.")
                    }
                }
            )
        }
    }

    fun logout() {
        _uiState.update { it.copy(currentUser = null, errorMessage = null) }
        refreshAdminSetupState()
    }

    fun adminAddUser(
        username: String,
        displayName: String,
        role: UserRole,
        pin: String,
        onResult: (Result<UserEntity>) -> Unit
    ) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            onResult(repository.adminCreateUser(actor, username, displayName, role, pin))
        }
    }

    fun adminResetPin(targetUuid: String, newPin: String, onResult: (Result<Unit>) -> Unit) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            onResult(repository.adminResetUserPin(actor, targetUuid, newPin))
        }
    }

    fun adminToggleUser(targetUuid: String, onResult: (Result<Unit>) -> Unit) {
        val actor = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            onResult(repository.adminToggleUserActive(actor, targetUuid))
        }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }
}
