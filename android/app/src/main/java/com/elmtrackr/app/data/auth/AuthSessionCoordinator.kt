package com.elmtrackr.app.data.auth

import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionCoordinator @Inject constructor(
    private val legacyDataAdopter: LegacyDataAdopter,
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val syncTrigger: SyncTrigger,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    suspend fun onUserAuthenticated(userId: String) {
        legacyDataAdopter.adoptFor(userId)
        applicationScope.launch {
            runCatching {
                val settings = settingsRepository.getSettings(userId)
                if (settings?.onboardingCompleted == true) {
                    compensationProfilesRepository.ensureMigrated(userId)
                }
            }
        }
        if (SupabaseClientProvider.isConfigured()) {
            syncTrigger.schedule()
        }
    }
}
