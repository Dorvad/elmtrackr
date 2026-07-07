package com.elmtrackr.app.ui.auth

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UiText

sealed interface AuthUiState {
    /** Initial state while the SDK checks for a stored session. */
    data object Loading : AuthUiState

    /** SUPABASE_URL / SUPABASE_ANON_KEY not set — app runs in local-only mode. */
    data object NotConfigured : AuthUiState

    /** No active session. Ready for sign-in or sign-up. */
    data class SignedOut(
        val isLoading: Boolean = false,
        val errorMessage: UiText? = null,
    ) : AuthUiState

    /** Active session. */
    data class SignedIn(
        val profile: Profile,
        val isLoading: Boolean = false,
    ) : AuthUiState

    /** Password-reset email was sent successfully. */
    data object PasswordResetSent : AuthUiState

    /** Recovery link opened — user must choose a new password. */
    data class PasswordRecovery(
        val isLoading: Boolean = false,
        val errorMessage: UiText? = null,
    ) : AuthUiState

    /** Sign-up succeeded but the Supabase project requires email confirmation. */
    data class SignUpConfirmation(val email: String) : AuthUiState
}
