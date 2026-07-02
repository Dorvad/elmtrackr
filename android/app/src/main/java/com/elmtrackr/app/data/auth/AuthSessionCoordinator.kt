package com.elmtrackr.app.data.auth

import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionCoordinator @Inject constructor(
    private val legacyDataAdopter: LegacyDataAdopter,
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
) : SessionBootstrapGate {
    private val _sessionBootstrapComplete = MutableStateFlow(false)
    override val sessionBootstrapComplete: StateFlow<Boolean> = _sessionBootstrapComplete.asStateFlow()

    suspend fun onUserAuthenticated(userId: String) {
        _sessionBootstrapComplete.value = false
        legacyDataAdopter.adoptFor(userId)
        if (SupabaseClientProvider.isConfigured()) {
            runCatching { syncRepository.syncAll(userId) }
            syncTrigger.schedule()
        }
        runCatching {
            val settings = settingsRepository.getSettings(userId)
            if (settings?.onboardingCompleted == true) {
                compensationProfilesRepository.ensureMigrated(userId)
            }
        }
        _sessionBootstrapComplete.value = true
    }

    fun resetSession() {
        _sessionBootstrapComplete.value = false
    }
}
