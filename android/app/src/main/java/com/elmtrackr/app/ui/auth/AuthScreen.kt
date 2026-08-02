package com.elmtrackr.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.design.AppLogo
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.design.auroraSubScreenTransition

private const val MIN_PASSWORD_LENGTH = 6

private enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

private fun authMotionOrder(state: AuthUiState): Int = when (state) {
    AuthUiState.Loading -> 0
    AuthUiState.NotConfigured -> 1
    is AuthUiState.SignedOut -> 2
    AuthUiState.PasswordResetSent -> 3
    is AuthUiState.SignUpConfirmation -> 4
    is AuthUiState.PasswordRecovery -> 5
    is AuthUiState.SignedIn -> 6
}

private fun authContentKey(state: AuthUiState): String = when (state) {
    AuthUiState.Loading -> "loading"
    AuthUiState.NotConfigured -> "not-configured"
    is AuthUiState.SignedOut -> "signed-out"
    is AuthUiState.SignedIn -> "signed-in"
    AuthUiState.PasswordResetSent -> "password-reset-sent"
    is AuthUiState.PasswordRecovery -> "password-recovery"
    is AuthUiState.SignUpConfirmation -> "sign-up-confirmation"
}

private fun authModeOrder(mode: AuthMode): Int = when (mode) {
    AuthMode.SIGN_IN -> 0
    AuthMode.SIGN_UP -> 1
    AuthMode.FORGOT_PASSWORD -> 2
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val motionEnabled = auroraMotionEnabled()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { authContentKey(it) },
            transitionSpec = {
                if (initialState is AuthUiState.Loading || targetState is AuthUiState.Loading) {
                    fadeIn(tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut)) togetherWith
                        fadeOut(tween(AuroraMotion.ContentCrossfadeMillis))
                } else {
                    auroraSubScreenTransition(authMotionOrder(targetState) > authMotionOrder(initialState), motionEnabled)
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "auth-screen",
        ) { s ->
            when (s) {
                is AuthUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is AuthUiState.NotConfigured -> NotConfiguredContent()
                is AuthUiState.SignedIn -> SignedInContent(
                    profile = s.profile,
                    isLoading = s.isLoading,
                    onSignOut = viewModel::signOut,
                )
                is AuthUiState.SignedOut -> SignedOutContent(
                    isLoading = s.isLoading,
                    errorMessage = s.errorMessage,
                    onSignIn = { email, password -> viewModel.signIn(email, password) },
                    onSignUp = { email, password -> viewModel.signUp(email, password) },
                    onResetPassword = { email -> viewModel.resetPassword(email) },
                    onClearError = viewModel::clearError,
                )
                is AuthUiState.PasswordResetSent -> {
                    BackHandler(onBack = viewModel::dismissPasswordReset)
                    PasswordResetSentContent(
                        onBack = viewModel::dismissPasswordReset,
                    )
                }
                is AuthUiState.PasswordRecovery -> {
                    BackHandler(onBack = viewModel::dismissPasswordRecovery)
                    PasswordRecoveryContent(
                        isLoading = s.isLoading,
                        errorMessage = s.errorMessage,
                        onUpdatePassword = viewModel::updatePassword,
                        onBack = viewModel::dismissPasswordRecovery,
                    )
                }
                is AuthUiState.SignUpConfirmation -> {
                    BackHandler(onBack = viewModel::dismissSignUpConfirmation)
                    SignUpConfirmationContent(
                        email = s.email,
                        onBack = viewModel::dismissSignUpConfirmation,
                    )
                }
            }
        }
    }
}

// ── Aurora bolt logo ──────────────────────────────────────────────────────────

@Composable
private fun AuroraBoltLogo() {
    AppLogo(
        modifier = Modifier.size(56.dp),
        cornerRadius = 16.dp,
    )
}

// ── Not configured ────────────────────────────────────────────────────────────

@Composable
private fun NotConfiguredContent() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.outline,
                modifier           = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text       = stringResource(R.string.auth_not_configured_title),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = stringResource(R.string.auth_not_configured_body),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Signed in ─────────────────────────────────────────────────────────────────

@Composable
private fun SignedInContent(
    profile: com.elmtrackr.app.domain.model.Profile,
    isLoading: Boolean,
    onSignOut: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            modifier            = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AuroraBoltLogo()
            Spacer(Modifier.height(20.dp))
            Text(
                text       = profile.fullName ?: profile.email,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (profile.fullName != null) {
                Text(
                    text  = profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick  = onSignOut,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.auth_sign_out))
            }
        }
    }
}

// ── Signed out form ───────────────────────────────────────────────────────────

@Composable
internal fun SignedOutContent(
    isLoading: Boolean,
    errorMessage: UiText?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode           by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email          by rememberSaveable { mutableStateOf("") }
    // Plain remember, not rememberSaveable: saved instance state is persisted by the
    // platform, so the credential would be written to disk. Losing a half-typed
    // password across process death is the cheaper trade.
    var password       by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val motionEnabled = auroraMotionEnabled()

    BackHandler(enabled = mode != AuthMode.SIGN_IN) {
        mode = AuthMode.SIGN_IN
        onClearError()
    }

    // remember: a fresh FocusRequester per recomposition is not attached to any node,
    // so requestFocus() from the email field's onNext threw or silently no-oped.
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager           = LocalFocusManager.current

    val emailError = if (email.isNotBlank() && '@' !in email) stringResource(R.string.auth_email_invalid) else null
    val passwordError = if (mode == AuthMode.SIGN_UP && password.isNotBlank() && password.length < MIN_PASSWORD_LENGTH)
        stringResource(R.string.auth_password_too_short, MIN_PASSWORD_LENGTH) else null

    val canSubmit = !isLoading &&
        email.isNotBlank() &&
        emailError == null &&
        passwordError == null &&
        (mode == AuthMode.FORGOT_PASSWORD || password.isNotBlank())

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier            = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .auroraEnter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Brand header
            AuroraBoltLogo()
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "ElmTrackr",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = stringResource(R.string.auth_tagline),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    auroraSubScreenTransition(authModeOrder(targetState) > authModeOrder(initialState), motionEnabled)
                },
                label = "auth-mode",
            ) { currentMode ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = when (currentMode) {
                            AuthMode.SIGN_IN -> stringResource(R.string.auth_sign_in)
                            AuthMode.SIGN_UP -> stringResource(R.string.auth_create_account)
                            AuthMode.FORGOT_PASSWORD -> stringResource(R.string.auth_reset_password)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; onClearError() },
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                        isError = emailError != null,
                        supportingText = emailError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (currentMode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() },
                            onDone = {
                                focusManager.clearFocus()
                                if (canSubmit) onResetPassword(email)
                            },
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (currentMode != AuthMode.FORGOT_PASSWORD) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; onClearError() },
                            label = { Text(stringResource(R.string.auth_password_label)) },
                            isError = passwordError != null,
                            supportingText = passwordError?.let { { Text(it) } },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) stringResource(R.string.auth_hide_password) else stringResource(R.string.auth_show_password),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (canSubmit) {
                                        if (currentMode == AuthMode.SIGN_IN) onSignIn(email, password)
                                        else onSignUp(email, password)
                                    }
                                },
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester),
                        )
                    }

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    ElmGradientButton(
                        onClick = {
                            focusManager.clearFocus()
                            when (currentMode) {
                                AuthMode.SIGN_IN -> onSignIn(email, password)
                                AuthMode.SIGN_UP -> onSignUp(email, password)
                                AuthMode.FORGOT_PASSWORD -> onResetPassword(email)
                            }
                        },
                        enabled = canSubmit,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(
                                when (currentMode) {
                                    AuthMode.SIGN_IN -> stringResource(R.string.auth_sign_in)
                                    AuthMode.SIGN_UP -> stringResource(R.string.auth_create_account)
                                    AuthMode.FORGOT_PASSWORD -> stringResource(R.string.auth_send_reset_email)
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when (currentMode) {
                        AuthMode.SIGN_IN -> {
                            TextButton(onClick = { mode = AuthMode.SIGN_UP; onClearError() }) {
                                Text(stringResource(R.string.auth_no_account_sign_up), color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { mode = AuthMode.FORGOT_PASSWORD; onClearError() }) {
                                Text(stringResource(R.string.auth_forgot_password), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AuthMode.SIGN_UP -> {
                            TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                                Text(stringResource(R.string.auth_have_account_sign_in), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        AuthMode.FORGOT_PASSWORD -> {
                            TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                                Text(stringResource(R.string.auth_back_to_sign_in), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Password recovery (deep link) ─────────────────────────────────────────────

@Composable
private fun PasswordRecoveryContent(
    isLoading: Boolean,
    errorMessage: UiText?,
    onUpdatePassword: (String) -> Unit,
    onBack: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val passwordError = if (password.isNotBlank() && password.length < MIN_PASSWORD_LENGTH)
        stringResource(R.string.auth_password_too_short, MIN_PASSWORD_LENGTH) else null
    val confirmError = if (confirmPassword.isNotBlank() && password != confirmPassword)
        stringResource(R.string.auth_passwords_do_not_match) else null
    val canSubmit = !isLoading &&
        password.isNotBlank() &&
        confirmPassword.isNotBlank() &&
        passwordError == null &&
        confirmError == null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .auroraEnter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuroraBoltLogo()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.auth_choose_new_password),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_new_password_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_new_password_label)) },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) stringResource(R.string.auth_hide_password) else stringResource(R.string.auth_show_password),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                isError = confirmError != null,
                supportingText = confirmError?.let { { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (canSubmit) onUpdatePassword(password)
                    },
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            ElmGradientButton(
                onClick = {
                    focusManager.clearFocus()
                    onUpdatePassword(password)
                },
                enabled = canSubmit,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(stringResource(R.string.auth_update_password), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.auth_back_to_sign_in), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ── Password reset sent ───────────────────────────────────────────────────────

@Composable
private fun PasswordResetSentContent(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            modifier            = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AuroraBoltLogo()
            Spacer(Modifier.height(20.dp))
            Text(
                text       = stringResource(R.string.auth_check_your_email),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = stringResource(R.string.auth_reset_link_sent),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.auth_back_to_sign_in), color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SignUpConfirmationContent(email: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuroraBoltLogo()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.auth_confirm_your_email), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.auth_confirmation_link_sent, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.auth_back_to_sign_in), color = MaterialTheme.colorScheme.primary) }
        }
    }
}
