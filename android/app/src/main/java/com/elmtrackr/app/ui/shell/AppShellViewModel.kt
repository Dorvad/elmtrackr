package com.elmtrackr.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.auth.SessionBootstrapGate
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionBootstrapGate: SessionBootstrapGate,
) : ViewModel() {

    val navState: StateFlow<AppNavState> = combine(
        authRepository.observePasswordRecoveryRequired(),
        authRepository.observeCurrentProfile(),
        sessionBootstrapGate.sessionBootstrapComplete,
    ) { recoveryRequired, profile, bootstrapComplete ->
        Triple(recoveryRequired, profile, bootstrapComplete)
    }.flatMapLatest { (recoveryRequired, profile, bootstrapComplete) ->
        when {
            !authRepository.isConfigured() -> flowOf(AppNavState.Auth)
            recoveryRequired -> flowOf(AppNavState.Auth)
            profile == null -> flowOf(AppNavState.Auth)
            else -> settingsRepository.observeSettings(profile.id).map { settings ->
                when {
                    settings == null && !bootstrapComplete -> AppNavState.Loading
                    settings?.onboardingCompleted == true -> AppNavState.Main
                    else -> AppNavState.Onboarding
                }
            }
        }
    }
        .catch { emit(AppNavState.Auth) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppNavState.Loading,
        )
}
