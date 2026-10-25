package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.data.sync.SyncHealth
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSetupChecklistPreferences
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeSyncRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.fake.FakeWidgetPinInspector
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The dashboard speaks up about sync only when someone has to act. Offline is the
 * normal case for an offline-first app and must stay silent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncAttentionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `changes merely waiting to upload raise nothing`() {
        assertNull(SyncAttention.of(SyncHealth(pendingCount = 12, failedCount = 0), "SyncWaiting: 12"))
        assertNull(SyncAttention.of(SyncHealth(0, 0), "Not configured"))
        assertNull(SyncAttention.of(null, null))
    }

    @Test
    fun `rejected changes and an expired session do`() {
        assertEquals(SyncAttention.UploadFailed(2), SyncAttention.of(SyncHealth(5, 2), "SyncedUnsent: 2"))
        assertEquals(
            SyncAttention.SessionExpired,
            SyncAttention.of(SyncHealth(3, 0), "Session expired — sign in again to resume syncing. Your data is safe on this device."),
        )
    }

    @Test
    fun `the dashboard state follows the repository`() = runTest {
        val sync = FakeSyncRepository()
        val auth = FakeAuthRepository().apply {
            setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
        }
        val vm = DashboardViewModel(
            FakeShiftsRepository(), FakeSettingsRepository(), FakeReportsRepository(), auth,
            FakeCompensationProfilesRepository(), FakeTasksRepository(), FakeAppPreferencesStore(),
            FakePremiumProfilesRepository(), FakeSetupChecklistPreferences(), FakeWidgetPinInspector(),
            com.elmtrackr.app.fake.FakeProjectsRepository(), com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences(),
            com.elmtrackr.app.fake.FakeReviewPromptRecorder(),
            computationDispatcher = mainDispatcherRule.dispatcher,
            syncRepository = sync,
        )
        val job = launch { vm.syncAttention.collect {} }

        sync.setHealth(pending = 4, failed = 0)
        advanceUntilIdle()
        assertNull(vm.syncAttention.value)

        sync.setHealth(pending = 4, failed = 1)
        advanceUntilIdle()
        assertEquals(SyncAttention.UploadFailed(1), vm.syncAttention.value)
        job.cancel()
    }
}
