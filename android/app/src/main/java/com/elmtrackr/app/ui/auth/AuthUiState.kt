package com.elmtrackr.app.ui.auth

import com.elmtrackr.app.domain.model.Profile

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val profile: Profile) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
