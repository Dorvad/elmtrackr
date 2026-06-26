package com.elmtrackr.app.ui.onboarding

sealed interface OnboardingUiState {
    data object Welcome : OnboardingUiState
    data object Saving : OnboardingUiState
    data object Completed : OnboardingUiState
    data class ValidationError(val errors: Map<String, String>) : OnboardingUiState
}
