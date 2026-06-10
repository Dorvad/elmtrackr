package com.elmtrackr.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val s = state) {
            is AuthUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
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

            is AuthUiState.PasswordResetSent -> PasswordResetSentContent(
                onBack = viewModel::dismissPasswordReset,
            )
        }
    }
}

@Composable
private fun NotConfiguredContent() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Auth not configured",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties to enable sign-in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SignedInContent(
    profile: com.elmtrackr.app.domain.model.Profile,
    isLoading: Boolean,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = profile.fullName ?: profile.email,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = profile.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = onSignOut,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator()
            else Text("Sign out")
        }
    }
}

@Composable
private fun SignedOutContent(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (mode) {
                AuthMode.SIGN_IN -> "Sign in"
                AuthMode.SIGN_UP -> "Create account"
                AuthMode.FORGOT_PASSWORD -> "Reset password"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; onClearError() },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (mode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode != AuthMode.FORGOT_PASSWORD) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (mode == AuthMode.SIGN_IN) onSignIn(email, password)
                        else onSignUp(email, password)
                    },
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                when (mode) {
                    AuthMode.SIGN_IN -> onSignIn(email, password)
                    AuthMode.SIGN_UP -> onSignUp(email, password)
                    AuthMode.FORGOT_PASSWORD -> onResetPassword(email)
                }
            },
            enabled = !isLoading && email.isNotBlank() &&
                (mode == AuthMode.FORGOT_PASSWORD || password.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator()
            else Text(
                when (mode) {
                    AuthMode.SIGN_IN -> "Sign in"
                    AuthMode.SIGN_UP -> "Create account"
                    AuthMode.FORGOT_PASSWORD -> "Send reset email"
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        when (mode) {
            AuthMode.SIGN_IN -> {
                TextButton(onClick = { mode = AuthMode.SIGN_UP; onClearError() }) {
                    Text("Don't have an account? Sign up")
                }
                TextButton(onClick = { mode = AuthMode.FORGOT_PASSWORD; onClearError() }) {
                    Text("Forgot password?")
                }
            }
            AuthMode.SIGN_UP -> {
                TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                    Text("Already have an account? Sign in")
                }
            }
            AuthMode.FORGOT_PASSWORD -> {
                TextButton(onClick = { mode = AuthMode.SIGN_IN; onClearError() }) {
                    Text("Back to sign in")
                }
            }
        }
    }
}

@Composable
private fun PasswordResetSentContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Check your email",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A password reset link has been sent to your email address.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("Back to sign in") }
    }
}
