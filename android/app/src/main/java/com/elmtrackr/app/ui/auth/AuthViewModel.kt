package com.elmtrackr.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<AuthUiState> =
        authRepository.observeCurrentProfile()
            .map { profile ->
                if (profile != null) AuthUiState.SignedIn(profile)
                else AuthUiState.SignedOut
            }
            .catch { e -> emit(AuthUiState.Error(e.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AuthUiState.Loading,
            )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
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
