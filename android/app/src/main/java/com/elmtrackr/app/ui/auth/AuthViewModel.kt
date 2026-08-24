package com.elmtrackr.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult
import com.elmtrackr.app.domain.auth.GoogleSignInClient
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.ui.common.UserFacingError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInClient: GoogleSignInClient,
) : ViewModel() {

    /**
     * One switch, decided at build time: a build carrying a Google Web client id
     * is a build whose Supabase project has the Google provider turned on, because
     * the two are configured together. Anything less certain would put a button on
     * screen that can only fail, and a user cannot tell our misconfiguration from
     * their own mistake.
     */
    private val googleSignInAvailable: Boolean =
        authRepository.isConfigured() && googleSignInClient.isConfigured

    private val _isLoading = MutableStateFlow(false)
    private val _googleLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<UiText?>(null)
    private val _passwordResetSent = MutableStateFlow(false)
    private val _signUpEmail = MutableStateFlow<String?>(null)
    private val _recoveryError = MutableStateFlow<UiText?>(null)

    init {
        authRepository.deepLinkErrors
            .onEach { message ->
                _error.value = message
                _recoveryError.value = message
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<AuthUiState> = combine(
        combine(
            authRepository.observePasswordRecoveryRequired(),
            authRepository.observeCurrentProfile(),
            _isLoading,
            _googleLoading,
        ) { recovery, profile, loading, googleLoading ->
            Quad(recovery, profile, loading, googleLoading)
        },
        combine(_error, _passwordResetSent, _signUpEmail, _recoveryError) { e, r, signup, recoveryErr ->
            Quad(e, r, signup, recoveryErr)
        },
    ) { (recoveryRequired, profile, isLoading, isGoogleLoading), (error, resetSent, signupEmail, recoveryError) ->
        when {
            !authRepository.isConfigured() -> AuthUiState.NotConfigured
            recoveryRequired -> AuthUiState.PasswordRecovery(
                isLoading = isLoading,
                errorMessage = recoveryError,
            )
            resetSent -> AuthUiState.PasswordResetSent
            profile != null -> AuthUiState.SignedIn(profile = profile, isLoading = isLoading)
            signupEmail != null -> AuthUiState.SignUpConfirmation(signupEmail)
            else -> AuthUiState.SignedOut(
                isLoading = isLoading,
                isGoogleLoading = isGoogleLoading,
                googleSignInAvailable = googleSignInAvailable,
                errorMessage = error,
            )
        }
    }.catch {
        emit(
            AuthUiState.SignedOut(
                googleSignInAvailable = googleSignInAvailable,
                errorMessage = UiText.Res(R.string.auth_error_session_check),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState.Loading,
    )

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = authRepository.signIn(email, password)) {
                // Profile flow drives navigation to SignedIn — keep the spinner
                // up until it emits so the form never looks idle mid-transition.
                is AuthResult.Success -> withTimeoutOrNull(SIGN_IN_PROPAGATION_TIMEOUT_MS) {
                    authRepository.observeCurrentProfile().filterNotNull().first()
                }
                is AuthResult.NotConfigured -> _error.value = UiText.Res(R.string.auth_error_not_configured)
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
                is AuthResult.Success -> _signUpEmail.value = email.trim() // show "check your email"
                is AuthResult.NotConfigured -> _error.value = UiText.Res(R.string.auth_error_not_configured)
                is AuthResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    /**
     * Sign in — or sign up, they are the same thing here — with Google.
     *
     * @param activityContext the hosting Activity. Credential Manager puts a
     *   sheet over it; it is not retained past this call.
     */
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _googleLoading.value = true
            _error.value = null
            when (val credential = googleSignInClient.requestIdToken(activityContext)) {
                is GoogleIdTokenResult.Success -> exchangeGoogleToken(credential)

                // The user closed the sheet. That is an answer, not a failure, and
                // an error message here would blame them for changing their mind.
                GoogleIdTokenResult.Cancelled -> Unit

                // Nothing on the device to offer, or no Credential Manager at all.
                // The hosted flow works from any browser and needs no account set
                // up on the device, so send them there rather than to a dead end.
                GoogleIdTokenResult.NoAccountOnDevice,
                GoogleIdTokenResult.ProviderUnavailable -> startBrowserSignIn()

                is GoogleIdTokenResult.Failure -> _error.value = UserFacingError.message(
                    credential.error,
                    R.string.auth_error_google_sign_in,
                )
            }
            _googleLoading.value = false
        }
    }

    private suspend fun exchangeGoogleToken(credential: GoogleIdTokenResult.Success) {
        val result = authRepository.signInWithGoogle(credential.idToken, credential.rawNonce)
        when (result) {
            // Same as the email path: hold the spinner until the profile lands so
            // the screen never sits looking idle mid-transition.
            is AuthResult.Success -> withTimeoutOrNull(SIGN_IN_PROPAGATION_TIMEOUT_MS) {
                authRepository.observeCurrentProfile().filterNotNull().first()
            }
            is AuthResult.NotConfigured -> _error.value = UiText.Res(R.string.auth_error_not_configured)
            is AuthResult.Error -> _error.value = result.message
        }
    }

    private suspend fun startBrowserSignIn() {
        when (val result = authRepository.startGoogleBrowserSignIn()) {
            // The browser is now on top. The session arrives through the auth deep
            // link, so there is nothing further to wait for here.
            is AuthResult.Success -> Unit
            is AuthResult.NotConfigured -> _error.value = UiText.Res(R.string.auth_error_not_configured)
            is AuthResult.Error -> _error.value = result.message
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
                is AuthResult.NotConfigured -> _error.value = UiText.Res(R.string.auth_error_not_configured)
                is AuthResult.Error -> _error.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun dismissPasswordReset() {
        _passwordResetSent.value = false
    }

    fun dismissSignUpConfirmation() {
        _signUpEmail.value = null
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _recoveryError.value = null
            when (val result = authRepository.updatePassword(newPassword)) {
                is AuthResult.Success -> Unit
                is AuthResult.NotConfigured -> _recoveryError.value = UiText.Res(R.string.auth_error_not_configured)
                is AuthResult.Error -> _recoveryError.value = result.message
            }
            _isLoading.value = false
        }
    }

    fun dismissPasswordRecovery() {
        viewModelScope.launch {
            authRepository.signOut()
            _recoveryError.value = null
        }
    }

    fun clearError() {
        _error.value = null
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private companion object {
        const val SIGN_IN_PROPAGATION_TIMEOUT_MS = 10_000L
    }
}
