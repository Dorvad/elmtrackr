package com.elmtrackr.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val navState: StateFlow<AppNavState> = combine(
        authRepository.observePasswordRecoveryRequired(),
        authRepository.observeCurrentProfile(),
    ) { recoveryRequired, profile ->
        recoveryRequired to profile
    }.flatMapLatest { (recoveryRequired, profile) ->
        when {
            !authRepository.isConfigured() -> flowOf(AppNavState.Auth)
            recoveryRequired -> flowOf(AppNavState.Auth)
            profile == null -> flowOf(AppNavState.Auth)
            else -> settingsRepository.observeSettings(profile.id).map { settings ->
                if (settings?.onboardingCompleted == true) AppNavState.Main
                else AppNavState.Onboarding
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppNavState.Loading,
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                AppShellViewModel(
                    authRepository = app.authRepository,
                    settingsRepository = app.settingsRepository,
                )
            }
        }
    }
}
