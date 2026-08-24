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
import com.elmtrackr.app.domain.leave.LeavePresets
import com.elmtrackr.app.domain.leave.SickPayCalculator
import com.elmtrackr.app.domain.leave.SickPayOption
import com.elmtrackr.app.domain.leave.SickPayOptions
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeWorkplacesRepository
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
    private val workplacesRepo = FakeWorkplacesRepository()

    private fun buildVm() =
        CompensationSettingsViewModel(compensationRepo, settingsRepo, authRepo, workplacesRepo)

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

    // ── Sick pay ──────────────────────────────────────────────────────────────

    private fun formValues(
        p: CompensationProfile,
        sickLeave: com.elmtrackr.app.domain.model.SickLeavePolicy,
    ) = CompensationFormValues(
        name = p.name,
        regionCode = p.regionCode,
        currencyCode = p.currencyCode,
        timezone = p.timezone,
        hourlyRate = p.baseHourlyRate,
        stackingPolicy = p.stackingPolicy,
        rules = p.rules,
        sickLeave = sickLeave,
        color = p.color ?: "#5B4DF2",
        icon = p.icon ?: "💼",
    )

    /**
     * With no policy stored, the screen has to show what the workplace will
     * actually be given the first time an absence is priced against it — the
     * region preset — rather than an empty ladder.
     */
    @Test
    fun `an unconfigured workplace reports the region preset ladder`() = runTest {
        compensationRepo.setProfiles(profile("p1", "Main job", isDefault = true))
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()

        val ready = lastReady(states)
        assertNotNull(ready)
        assertEquals(LeavePresets.israeliSickTiers, ready!!.sickLeave.payTiers)
        assertEquals(SickPayOption.REGION_STANDARD, SickPayOptions.of(ready.sickLeave.payTiers, RegionCode.IL))
        job.cancel()
    }

    /**
     * The defect this closes: `updatePolicyRules` existed on the repository and
     * had no caller anywhere in the app, so an Israeli user whose employer pays
     * sick leave from day one had no way to say so and was stuck with a statutory
     * unpaid first day.
     */
    @Test
    fun `saving from-day-one sick pay writes it to the workplace policy`() = runTest {
        val p = profile("p1", "Main job", isDefault = true)
        compensationRepo.setProfiles(p)
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.saveProfile(
            formValues(
                p,
                com.elmtrackr.app.domain.model.SickLeavePolicy(
                    enabled = true,
                    payTiers = SickPayOptions.tiersFor(SickPayOption.FROM_DAY_ONE, RegionCode.IL)!!,
                ),
            ),
        )
        advanceUntilIdle()

        val workplaceId = workplacesRepo.getDefaultWorkplace("u1")?.id
        assertNotNull(workplaceId)
        val inForce = workplacesRepo.resolvePolicy(workplaceId!!, Instant.now())
        assertNotNull(inForce)
        assertEquals(1.0, SickPayCalculator.resolveTier(inForce!!.rules.sick.payTiers, 1)?.multiplier)
        assertEquals(
            SickPayOption.FROM_DAY_ONE,
            SickPayOptions.of(inForce.rules.sick.payTiers, RegionCode.IL),
        )
        job.cancel()
    }

    /** Only the sick block moves; the rest of the leave policy carries over. */
    @Test
    fun `saving sick pay leaves the rest of the leave policy alone`() = runTest {
        val p = profile("p1", "Main job", isDefault = true)
        compensationRepo.setProfiles(p)
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.saveProfile(
            formValues(
                p,
                com.elmtrackr.app.domain.model.SickLeavePolicy(
                    enabled = true,
                    payTiers = LeavePresets.fullPayFromDayOneTiers,
                ),
            ),
        )
        advanceUntilIdle()

        val workplaceId = workplacesRepo.getDefaultWorkplace("u1")!!.id
        val inForce = workplacesRepo.resolvePolicy(workplaceId, Instant.now())!!
        val preset = LeavePresets.forRegion(RegionCode.IL)
        assertEquals(preset.vacation, inForce.rules.vacation)
        assertEquals(preset.balanceUnit, inForce.rules.balanceUnit)
        job.cancel()
    }

    /**
     * The policy is effective-dated, so a change supersedes rather than rewrites:
     * an absence reported under the old arrangement keeps the explanation it was
     * priced with.
     */
    @Test
    fun `changing the arrangement supersedes the old policy instead of editing it`() = runTest {
        val p = profile("p1", "Main job", isDefault = true)
        compensationRepo.setProfiles(p)
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.saveProfile(
            formValues(p, com.elmtrackr.app.domain.model.SickLeavePolicy(payTiers = LeavePresets.fullPayFromDayOneTiers)),
        )
        advanceUntilIdle()

        val workplaceId = workplacesRepo.getDefaultWorkplace("u1")!!.id
        val stored = workplacesRepo.policiesFor(workplaceId)
        assertEquals(2, stored.size)
        assertEquals(1, stored.count { it.isActive })
        // The superseded row keeps the statutory ladder it was priced with.
        val closed = stored.single { !it.isActive }
        assertEquals(LeavePresets.israeliSickTiers, closed.rules.sick.payTiers)
        job.cancel()
    }

    /** Saving an unchanged arrangement writes nothing, so history stays short. */
    @Test
    fun `saving an unchanged arrangement does not supersede the policy`() = runTest {
        val p = profile("p1", "Main job", isDefault = true)
        compensationRepo.setProfiles(p)
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        val states = mutableListOf<CompensationSettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureLoaded()
        advanceUntilIdle()
        vm.saveProfile(formValues(p, lastReady(states)!!.sickLeave))
        advanceUntilIdle()

        val workplaceId = workplacesRepo.getDefaultWorkplace("u1")!!.id
        assertEquals(1, workplacesRepo.policiesFor(workplaceId).size)
        job.cancel()
    }
}
