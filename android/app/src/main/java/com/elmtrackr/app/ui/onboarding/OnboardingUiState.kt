package com.elmtrackr.app.ui.onboarding

import com.elmtrackr.app.domain.model.UserSettings

sealed interface OnboardingUiState {
    data object Welcome : OnboardingUiState
    data class Configuring(val draft: UserSettings) : OnboardingUiState
    data object Saving : OnboardingUiState
    data object Completed : OnboardingUiState
}
