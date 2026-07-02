package com.elmtrackr.app.data.auth

import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionCoordinator @Inject constructor(
    private val legacyDataAdopter: LegacyDataAdopter,
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionBootstrapGate {
    private val _sessionBootstrapComplete = MutableStateFlow(false)
    override val sessionBootstrapComplete: StateFlow<Boolean> = _sessionBootstrapComplete.asStateFlow()

    private val lock = Any()
    private var bootstrapJob: Job? = null
    private var bootstrapUserId: String? = null

    /**
     * Adopts legacy local data inline (must happen before the caller upserts the
     * auth profile), then runs the slow remote bootstrap in the background so
     * profile emission — and navigation away from the login screen — never waits
     * on the network. Deduped per user: concurrent collectors and session
     * re-emissions (e.g. token refresh) reuse the in-flight or completed bootstrap.
     */
    suspend fun onUserAuthenticated(userId: String) {
        legacyDataAdopter.adoptFor(userId)
        synchronized(lock) {
            val alreadyHandled = bootstrapUserId == userId &&
                (bootstrapJob?.isActive == true || _sessionBootstrapComplete.value)
            if (alreadyHandled) return
            bootstrapJob?.cancel()
            bootstrapUserId = userId
            _sessionBootstrapComplete.value = false
            bootstrapJob = scope.launch { bootstrap(userId) }
        }
    }

    private suspend fun bootstrap(userId: String) {
        if (SupabaseClientProvider.isConfigured()) {
            try {
                syncRepository.syncAll(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort — scheduled sync below will retry
            }
            syncTrigger.schedule()
        }
        try {
            val settings = settingsRepository.getSettings(userId)
            if (settings?.onboardingCompleted == true) {
                compensationProfilesRepository.ensureMigrated(userId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort
        }
        _sessionBootstrapComplete.value = true
    }

    fun resetSession() {
        synchronized(lock) {
            bootstrapJob?.cancel()
            bootstrapJob = null
            bootstrapUserId = null
            _sessionBootstrapComplete.value = false
        }
    }
}
