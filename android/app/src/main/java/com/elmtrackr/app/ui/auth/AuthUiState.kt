package com.elmtrackr.app.ui.auth

import com.elmtrackr.app.domain.model.Profile

sealed interface AuthUiState {
    /** Initial state while the SDK checks for a stored session. */
    data object Loading : AuthUiState

    /** SUPABASE_URL / SUPABASE_ANON_KEY not set — app runs in local-only mode. */
    data object NotConfigured : AuthUiState

    /** No active session. Ready for sign-in or sign-up. */
    data class SignedOut(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthUiState

    /** Active session. */
    data class SignedIn(
        val profile: Profile,
        val isLoading: Boolean = false,
    ) : AuthUiState

    /** Password-reset email was sent successfully. */
    data object PasswordResetSent : AuthUiState
}
