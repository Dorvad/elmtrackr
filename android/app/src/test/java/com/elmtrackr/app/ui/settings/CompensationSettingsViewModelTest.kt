package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CompensationSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val compensationRepo = FakeCompensationProfilesRepository()

    private fun buildVm() = CompensationSettingsViewModel(compensationRepo, settingsRepo, authRepo)

    private fun profile(id: String, name: String, isDefault: Boolean = false): CompensationProfile {
        val preset = RegionPresets.forRegion(RegionCode.IL)
        return CompensationProfile(
            id, "u1", name, RegionCode.IL, "ILS", "Asia/Jerusalem",
            50.0, preset.rules, preset.stackingPolicy, isDefault = isDefault,
        )
    }

    private fun settings() = UserSettings(
        id = "s1",
        userId = "u1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun lastReady(states: List<CompensationSettingsUiState>): CompensationSettingsUiState.Ready? =
        states.filterIsInstance<CompensationSettingsUiState.Ready>().lastOrNull()

    @Test
    fun `deleteProfile removes non-default profile and reselects default`() = runTest {
        compensationRepo.setProfiles(profile("p1", "Main job", isDefault = true), profile("p2", "Side gig"))
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.selectProfile("p2")
        vm.deleteProfile()
        advanceUntilIdle()

        assertEquals(listOf("p1"), compensationRepo.getProfiles("u1").map { it.id })
        val ready = lastReady(states)
        assertNotNull(ready)
        assertEquals("p1", ready!!.profile.id)
        assertEquals(UiText.Res(R.string.settings_feedback_profile_deleted), ready.saveMessage?.text)
        assertEquals(false, ready.saveMessage?.isError)
        job.cancel()
    }

    @Test
    fun `deleting the default profile promotes the next one and updates settings`() = runTest {
        compensationRepo.setProfiles(profile("p1", "Main job", isDefault = true), profile("p2", "Side gig"))
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.deleteProfile()
        advanceUntilIdle()

        val remaining = compensationRepo.getProfiles("u1")
        assertEquals(listOf("p2"), remaining.map { it.id })
        assertTrue(remaining.single().isDefault)
        assertEquals("p2", settingsRepo.getSettings("u1")?.defaultCompensationProfileId)
        val ready = lastReady(states)
        assertNotNull(ready)
        assertEquals("p2", ready!!.profile.id)
        job.cancel()
    }

    @Test
    fun `deleteProfile refuses to remove the only profile`() = runTest {
        compensationRepo.setProfiles(profile("p1", "Main job", isDefault = true))
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.deleteProfile()
        advanceUntilIdle()

        assertEquals(listOf("p1"), compensationRepo.getProfiles("u1").map { it.id })
        val ready = lastReady(states)
        assertNotNull(ready)
        assertEquals(UiText.Res(R.string.settings_feedback_delete_last_profile), ready!!.saveMessage?.text)
        assertEquals(true, ready.saveMessage?.isError)
        job.cancel()
    }
}
