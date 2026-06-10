package com.elmtrackr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _passwordResetSent = MutableStateFlow(false)

    val uiState: StateFlow<AuthUiState> = combine(
        combine(authRepository.observeCurrentProfile(), _isLoading) { p, l -> p to l },
        combine(_error, _passwordResetSent) { e, r -> e to r },
    ) { (profile, isLoading), (error, resetSent) ->
        when {
            !authRepository.isConfigured() -> AuthUiState.NotConfigured
            resetSent -> AuthUiState.PasswordResetSent
            profile != null -> AuthUiState.SignedIn(profile = profile, isLoading = isLoading)
            else -> AuthUiState.SignedOut(isLoading = isLoading, errorMessage = error)
        }
    }.catch { e ->
        emit(AuthUiState.SignedOut(errorMessage = e.message))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthUiState.Loading,
    )

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = authRepository.signIn(email, password)) {
                is AuthResult.Success -> Unit
                is AuthResult.NotConfigured -> _error.value = "Supabase is not configured"
                is AuthResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = authRepository.signUp(email, password)) {
                is AuthResult.Success -> Unit
                is AuthResult.NotConfigured -> _error.value = "Supabase is not configured"
                is AuthResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.signOut()
            _error.value = null
            _isLoading.value = false
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = authRepository.resetPassword(email)) {
                is AuthResult.Success -> _passwordResetSent.value = true
                is AuthResult.NotConfigured -> _error.value = "Supabase is not configured"
                is AuthResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun dismissPasswordReset() {
        _passwordResetSent.value = false
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                AuthViewModel(app.authRepository)
            }
        }
    }
}
