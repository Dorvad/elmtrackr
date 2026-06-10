package com.elmtrackr.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.ui.theme.ElmTrackrTheme

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit = {},
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState is OnboardingUiState.Completed) {
        onCompleted()
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (uiState) {
            is OnboardingUiState.Welcome,
            is OnboardingUiState.Configuring -> OnboardingForm(
                isSaving = false,
                onComplete = { rate, weekendDays ->
                    viewModel.completeOnboarding(rate, weekendDays)
                },
            )
            is OnboardingUiState.Saving -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {}
        }
    }
}

@Composable
private fun OnboardingForm(isSaving: Boolean, onComplete: (Double?, List<Int>) -> Unit) {
    var rateText by remember { mutableStateOf("") }
    val defaultWeekendDays = listOf(5, 6)  // Fri + Sat

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "Welcome to ElmTrackr",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set up your hourly rate to track earnings",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))

        OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it },
            label = { Text("Hourly rate (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onComplete(rateText.toDoubleOrNull(), defaultWeekendDays) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Get started")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    ElmTrackrTheme { OnboardingForm(false) { _, _ -> } }
}
