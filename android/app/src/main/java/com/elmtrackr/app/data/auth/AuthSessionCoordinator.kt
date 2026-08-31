package com.elmtrackr.app.data.auth

import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.monitoring.CrashReporting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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

    /**
     * Everything that has to happen once per signed-in session, bounded.
     *
     * The bound is the point. `sessionBootstrapComplete` is one of the three
     * inputs to `AppShellViewModel.navState`, and a user whose local settings
     * row has not arrived yet sits on `AppNavState.Loading` until this flips —
     * so a remote call that never returns is not a slow sync, it is an app that
     * never opens. Neither step had a deadline: a stalled socket (captive
     * portal, a carrier black-holing the connection, a server that accepts and
     * never answers) left the process waiting on a read with no timeout, and
     * the user waiting on a loading screen with no end.
     *
     * Both steps are already best-effort — every failure below is swallowed
     * because the scheduled sync retries — so a timeout joins the failures
     * rather than introducing a new outcome. The generous window is deliberate:
     * long enough that a genuinely slow first sync still completes and the user
     * lands where their data says they should, short enough to be a wait rather
     * than a dead end.
     *
     * [TimeoutCancellationException] is caught *before* [CancellationException]
     * on purpose — it is a subclass, and letting it fall through to the rethrow
     * would skip the completion below and reproduce exactly the hang this
     * timeout exists to prevent. A cancellation from anywhere else still
     * propagates: sign-out and [resetSession] must not open the gate.
     */
    private suspend fun bootstrap(userId: String) {
        if (SupabaseClientProvider.isConfigured()) {
            runBootstrapStep { syncRepository.syncAll(userId) }
            syncTrigger.schedule()
        }
        runBootstrapStep {
            val settings = settingsRepository.getSettings(userId)
            if (settings?.onboardingCompleted == true) {
                compensationProfilesRepository.ensureMigrated(userId)
            }
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

/**
 * How long either half of the session bootstrap may take before the app stops
 * waiting for it.
 *
 * Sized as a wait a person would tolerate on a bad connection rather than as a
 * network timeout: a first sync on mobile data can legitimately take tens of
 * seconds, and cutting it short would land a returning user on onboarding
 * instead of their own data. Past this, no answer is coming, and a loading
 * screen with no end is the worse outcome.
 */
internal const val REMOTE_BOOTSTRAP_TIMEOUT_MILLIS = 45_000L

/**
 * Runs one best-effort bootstrap step under a deadline, reporting whether it
 * finished rather than throwing.
 *
 * Extracted and internal so the ordering below can be tested, because the
 * ordering is the whole point. [TimeoutCancellationException] is a
 * [CancellationException]: caught in the wrong order it falls through to the
 * rethrow, the caller never reaches the line that opens
 * `sessionBootstrapComplete`, and the app is stranded on its loading screen —
 * precisely the failure the deadline was added to prevent. Cancellation from
 * anywhere else must still propagate, so that sign-out and `resetSession` do
 * not open the gate on a session that is going away.
 */
internal suspend fun runBootstrapStep(
    timeoutMillis: Long = REMOTE_BOOTSTRAP_TIMEOUT_MILLIS,
    step: suspend () -> Unit,
): Boolean =
    try {
        withTimeout(timeoutMillis) { step() }
        true
    } catch (e: TimeoutCancellationException) {
        // Stalled, not failed. The caller carries on; the scheduled sync retries.
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CrashReporting.report(e)
        false
    }
