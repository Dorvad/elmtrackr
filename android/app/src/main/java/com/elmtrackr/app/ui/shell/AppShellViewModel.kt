package com.elmtrackr.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AppShellViewModel(
    private val authRepository: AuthRepository,
    onboardingCompletedFlow: Flow<Boolean>,
) : ViewModel() {

    val navState: StateFlow<AppNavState> = combine(
        authRepository.observeCurrentProfile(),
        onboardingCompletedFlow,
    ) { profile, onboardingCompleted ->
        when {
            !authRepository.isConfigured() || profile != null ->
                if (onboardingCompleted) AppNavState.Main else AppNavState.Onboarding
            else -> AppNavState.Auth
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppNavState.Loading,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                AppShellViewModel(
                    authRepository = app.authRepository,
                    onboardingCompletedFlow = app.appPreferences.preferences.map { it.onboardingCompleted },
                )
            }
        }
    }
}
