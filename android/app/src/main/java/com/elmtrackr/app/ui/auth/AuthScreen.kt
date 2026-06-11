package com.elmtrackr.app.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPlum

private const val MIN_PASSWORD_LENGTH = 6

private enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

private val boltGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.5f to AuroraPlum, 1f to AuroraAqua),
)

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        when (val s = state) {
            is AuthUiState.Loading       -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AuroraIndigo)
            }
            is AuthUiState.NotConfigured -> NotConfiguredContent()
            is AuthUiState.SignedIn      -> SignedInContent(
                profile   = s.profile,
                isLoading = s.isLoading,
                onSignOut = viewModel::signOut,
            )
            is AuthUiState.SignedOut -> SignedOutContent(
                isLoading       = s.isLoading,
                errorMessage    = s.errorMessage,
                onSignIn        = { email, password -> viewModel.signIn(email, password) },
                onSignUp        = { email, password -> viewModel.signUp(email, password) },
                onResetPassword = { email -> viewModel.resetPassword(email) },
                onClearError    = viewModel::clearError,
            )
            is AuthUiState.PasswordResetSent -> PasswordResetSentContent(
                onBack = viewModel::dismissPasswordReset,
            )
        }
    }
}

// ── Aurora bolt logo ──────────────────────────────────────────────────────────

@Composable
private fun AuroraBoltLogo() {
    Box(
        modifier         = Modifier
            .size(56.dp)
            .background(boltGradient, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.Bolt,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(32.dp),
        )
    }
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
                tint               = AuroraFaint,
                modifier           = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "Auth not configured",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties to enable sign-in.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = AuroraInk2,
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
                    color = AuroraInk2,
                )
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick  = onSignOut,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Sign out")
            }
        }
    }
}

// ── Signed out form ───────────────────────────────────────────────────────────

@Composable
private fun SignedOutContent(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode           by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email          by rememberSaveable { mutableStateOf("") }
    var password       by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val passwordFocusRequester = FocusRequester()
    val focusManager           = LocalFocusManager.current

    val emailError = if (email.isNotBlank() && '@' !in email) "Enter a valid email address" else null
    val passwordError = if (mode == AuthMode.SIGN_UP && password.isNotBlank() && password.length < MIN_PASSWORD_LENGTH)
        "Password must be at least $MIN_PASSWORD_LENGTH characters" else null

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
                .padding(horizontal = 24.dp, vertical = 48.dp),
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
                text      = "Track shifts, overtime & earnings",
                style     = MaterialTheme.typography.bodyMedium,
                color     = AuroraInk2,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text       = when (mode) {
                    AuthMode.SIGN_IN        -> "Sign in"
                    AuthMode.SIGN_UP        -> "Create account"
                    AuthMode.FORGOT_PASSWORD -> "Reset password"
                },
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value        = email,
                onValueChange = { email = it; onClearError() },
                label        = { Text("Email") },
                leadingIcon  = { Icon(Icons.Filled.Email, contentDescription = null, tint = AuroraFaint) },
                isError      = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = if (mode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() },
                    onDone = {
                        focusManager.clearFocus()
                        if (canSubmit) onResetPassword(email)
                    },
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            if (mode != AuthMode.FORGOT_PASSWORD) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value        = password,
                    onValueChange = { password = it; onClearError() },
                    label        = { Text("Password") },
                    isError      = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector        = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint               = AuroraFaint,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSubmit) {
                                if (mode == AuthMode.SIGN_IN) onSignIn(email, password)
                                else onSignUp(email, password)
                            }
                        },
                    ),
                    singleLine = true,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                )
            }

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text     = it,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            ElmGradientButton(
                onClick  = {
                    focusManager.clearFocus()
                    when (mode) {
                        AuthMode.SIGN_IN         -> onSignIn(email, password)
                        AuthMode.SIGN_UP         -> onSignUp(email, password)
                        AuthMode.FORGOT_PASSWORD -> onResetPassword(email)
                    }
                },
                enabled  = canSubmit,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White,
                    )
                } else {
                    Text(
                        when (mode) {
                            AuthMode.SIGN_IN         -> "Sign in"
                            AuthMode.SIGN_UP         -> "Create account"
                            AuthMode.FORGOT_PASSWORD -> "Send reset email"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (mode) {
                AuthMode.SIGN_IN -> {
                    TextButton(onClick = { mode = AuthMode.SIGN_UP; onClearError() }) {
                        Text("Don't have an account? Sign up", color = AuroraIndigo)
                    }
                    TextButton(onClick = { mode = AuthMode.FORGOT_PASSWORD; onClearError() }) {
                        Text("Forgot password?", color = AuroraInk2)
                    }
                }
                AuthMode.SIGN_UP -> {
                    TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                        Text("Already have an account? Sign in", color = AuroraIndigo)
                    }
                }
                AuthMode.FORGOT_PASSWORD -> {
                    TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                        Text("Back to sign in", color = AuroraIndigo)
                    }
                }
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
                text       = "Check your email",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "A password reset link has been sent to your email address.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = AuroraInk2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) { Text("Back to sign in", color = AuroraIndigo) }
        }
    }
}
