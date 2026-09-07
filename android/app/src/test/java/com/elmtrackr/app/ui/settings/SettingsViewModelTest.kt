package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.billing.FreeClockFacePackEntitlements
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.fake.FakeAppLockPreferencesStore
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSyncRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.fake.FakeThemePreferenceStore
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val themeStore = FakeThemePreferenceStore()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val syncRepo = FakeSyncRepository()
    private val syncTrigger = FakeSyncTrigger()
    private val appLockPrefs = FakeAppLockPreferencesStore()
    private val clockFacePrefs = com.elmtrackr.app.fake.FakeClockFacePreferences()

    private fun buildVm() = SettingsViewModel(
        repo,
        authRepo,
        compensationRepo,
        themeStore,
        syncRepo,
        syncTrigger,
        appLockPrefs,
        clockFacePrefs,
        FreeClockFacePackEntitlements(),
        com.elmtrackr.app.fake.FakeClockFacePackStore(),
    )

    private fun defaultSettings() = UserSettings(
        id = "s1",
        userId = "u1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    fun `initial state is Loading`() {
        assertEquals(SettingsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `Ready state when settings exist`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals("s1", ready!!.settings.id)
        job.cancel()
    }

    @Test
    fun `ensureSettingsExist creates defaults when no settings`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureSettingsExist()
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals("u1", ready!!.settings.userId)
        job.cancel()
    }

    // ── saveSettings ──────────────────────────────────────────────────────────

    @Test
    fun `saveSettings converts hours to minutes`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 10.0, 50.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val saved = repo.getSettings("u1")
        assertEquals(600, saved?.dailyOvertimeThresholdMinutes)
        assertEquals(3000, saved?.weeklyOvertimeThresholdMinutes)
    }

    @Test
    fun `saveSettings updates hourly rate`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, 75.5, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(75.5, repo.getSettings("u1")?.hourlyRate)
    }

    @Test
    fun `saveSettings clears hourly rate when null`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings().copy(hourlyRate = 50.0))
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(null, repo.getSettings("u1")?.hourlyRate)
    }

    @Test
    fun `saveSettings updates currency`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, 50.0, "UTC", ClockStyle.CLASSIC, CurrencyCode.EUR, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(CurrencyCode.EUR, repo.getSettings("u1")?.currency)
    }

    @Test
    fun `saveSettings persists feature flags`() = runTest {
        val vm = buildVm()
        repo.setSettings(
            defaultSettings().copy(
                featuresTravelRefunds = false,
                featuresPaidProjects = false,
                featuresInsights = false,
                featuresClockStyles = true,
            ),
        )
        advanceUntilIdle()

        vm.saveSettings(
            "",
            8.0,
            40.0,
            null,
            "UTC",
            ClockStyle.CLASSIC,
            weekendDays = emptyList(),
            featureFlags = SettingsFeatureFlags(
                travelRefunds = true,
                paidProjects = true,
                insights = true,
                clockStyles = false,
                overtimeReminders = true,
            ),
        )
        advanceUntilIdle()

        val saved = repo.getSettings("u1")
        assertEquals(true, saved?.featuresTravelRefunds)
        assertEquals(true, saved?.featuresPaidProjects)
        assertEquals(true, saved?.featuresInsights)
        assertEquals(false, saved?.featuresClockStyles)
    }

    @Test
    fun `saveSettings updates display name in profile`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        authRepo.setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
        advanceUntilIdle()

        vm.saveSettings("Alice", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    @Test
    fun `saveSettings does not update name when unchanged`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        authRepo.setProfile(Profile("u1", "test@example.com", "Alice", Instant.EPOCH, Instant.EPOCH))
        advanceUntilIdle()

        vm.saveSettings("Alice", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    fun `validate zero daily OT returns error`() {
        assertTrue("dailyOt" in buildVm().validate(0.0, 40.0, null))
    }

    @Test
    fun `validate daily OT over 24 returns error`() {
        assertTrue("dailyOt" in buildVm().validate(25.0, 40.0, null))
    }

    @Test
    fun `validate zero weekly OT returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(8.0, 0.0, null))
    }

    @Test
    fun `validate weekly OT over 168 returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(8.0, 200.0, null))
    }

    @Test
    fun `validate weekly OT less than daily returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(10.0, 9.0, null))
    }

    @Test
    fun `validate negative hourly rate returns error`() {
        assertTrue("hourlyRate" in buildVm().validate(8.0, 40.0, -1.0))
    }

    @Test
    fun `validate with valid inputs returns no errors`() {
        assertTrue(buildVm().validate(8.0, 40.0, 50.0).isEmpty())
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    @Test
    fun `saveTheme reflected in Ready state`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveTheme("dark")
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertEquals("dark", ready?.selectedTheme)
        job.cancel()
    }

    // ── Save feedback ─────────────────────────────────────────────────────────

    @Test
    fun `saveSettings emits success feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(UiText.Res(R.string.settings_feedback_saved), ready!!.saveFeedback?.message)
        assertEquals(false, ready.saveFeedback?.isError)
        job.cancel()
    }

    @Test
    fun `saveSettings emits validation feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 0.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertTrue(ready!!.saveFeedback?.isError == true)
        assertTrue(ready.validationErrors.containsKey("dailyOt"))
        job.cancel()
    }

    @Test
    fun `clearSaveFeedback clears feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()
        vm.clearSaveFeedback()
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(null, ready!!.saveFeedback)
        job.cancel()
    }

    // ── Account ───────────────────────────────────────────────────────────────

    @Test
    fun `saveSettings propagates currency to default compensation profile`() = runTest {
        val profile = CompensationProfile(
            id = "cp1",
            userId = "u1",
            name = "Main job",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 50.0,
            rules = CompensationRules(),
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
            isDefault = true,
        )
        compensationRepo.setProfiles(profile)
        val vm = buildVm()
        repo.setSettings(defaultSettings().copy(hourlyRate = 50.0, currency = CurrencyCode.ILS))

        vm.saveSettings(
            displayName = "",
            dailyOtHours = 8.0,
            weeklyOtHours = 40.0,
            hourlyRate = 55.0,
            timezone = "Asia/Jerusalem",
            clockStyle = ClockStyle.CLASSIC,
            currency = CurrencyCode.USD,
            weekendDays = listOf(5, 6),
        )
        advanceUntilIdle()

        val saved = compensationRepo.getProfiles("u1").first()
        assertEquals("USD", saved.currencyCode)
        assertEquals(55.0, saved.baseHourlyRate)
    }

    @Test
    fun `resetPassword does not crash when no profile`() = runTest {
        val vm = buildVm()
        vm.resetPassword()
        advanceUntilIdle()
        // early return — no exception expected
    }

    /**
     * The appearance screen's four-face row is fed by this history, and the
     * history is written on save rather than on tap: tapping a tile only previews
     * a face, so recording there would fill the row with faces the user looked at
     * and rejected, and reorder it under their finger mid-choice.
     */
    @Test
    fun `saving records the chosen clock face as most recent`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.VINYL, weekendDays = emptyList())
        advanceUntilIdle()
        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.LUNA, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(listOf("LUNA", "VINYL"), clockFacePrefs.recentClockFaces)
    }

    /** Re-saving the same face must not stack duplicates in the history. */
    @Test
    fun `saving the same clock face twice records it once`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        repeat(3) {
            vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.TIDE, weekendDays = emptyList())
            advanceUntilIdle()
        }

        assertEquals(listOf("TIDE"), clockFacePrefs.recentClockFaces)
    }

    /**
     * A face removed in a later version leaves a name behind in the stored
     * history. It must vanish rather than resolve to Classic, which is what
     * ClockStyle.fromPersisted would have done.
     */
    @Test
    fun `unknown stored face names are dropped from the exposed history`() = runTest {
        val prefs = com.elmtrackr.app.fake.FakeClockFacePreferences(
            initial = listOf("LUNA", "FELLOWSHIP", "TIDE"),
        )
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs, prefs,
            FreeClockFacePackEntitlements(),
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().last()
        assertEquals(listOf(ClockStyle.LUNA, ClockStyle.TIDE), ready.recentClockFaces)
        job.cancel()
    }

    // ── Clock face packs ──────────────────────────────────────────────────────

    @Test
    fun `installing a pack records it`() = runTest {
        val vm = buildVm()

        vm.installClockFacePack(ClockFaceGroup.NATURE)
        advanceUntilIdle()

        assertEquals(setOf("NATURE"), clockFacePrefs.installedClockFacePacks)
    }

    /**
     * The entitlement check lives in the ViewModel so a future paid pack cannot be
     * added by a screen that forgot to ask. This is the test that would fail if
     * someone later moved the check into the UI.
     */
    @Test
    fun `a pack the user is not entitled to is not installed`() = runTest {
        val entitlements = object : com.elmtrackr.app.billing.ClockFacePackEntitlements {
            override suspend fun isEntitled(pack: ClockFaceGroup) = false
            override suspend fun anyPackRequiresPurchase() = true
            override fun observeOwned() = kotlinx.coroutines.flow.flowOf(emptySet<ClockFaceGroup>())
        }
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, entitlements,
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )

        vm.installClockFacePack(ClockFaceGroup.NATURE)
        advanceUntilIdle()

        assertEquals(emptySet<String>(), clockFacePrefs.installedClockFacePacks)
    }

    @Test
    fun `removing a pack drops it and leaves the others`() = runTest {
        val prefs = com.elmtrackr.app.fake.FakeClockFacePreferences(
            initialPacks = setOf("NATURE", "JOURNEYS"),
        )
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            prefs, FreeClockFacePackEntitlements(),
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )

        vm.removeClockFacePack(ClockFaceGroup.NATURE, selected = ClockStyle.CLASSIC)
        advanceUntilIdle()

        assertEquals(setOf("JOURNEYS"), prefs.installedClockFacePacks)
    }

    /**
     * Removing the pack that holds the selected face has to move the selection, or
     * the next save would store a face the user no longer has.
     */
    @Test
    fun `removing the pack holding the selected face resets the selection`() = runTest {
        val prefs = com.elmtrackr.app.fake.FakeClockFacePreferences(initialPacks = setOf("JOURNEYS"))
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            prefs, FreeClockFacePackEntitlements(),
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )
        var resetTo: ClockStyle? = null

        vm.removeClockFacePack(ClockFaceGroup.JOURNEYS, selected = ClockStyle.VINYL) { resetTo = it }
        advanceUntilIdle()

        assertEquals(ClockStyle.CLASSIC, resetTo)
        assertEquals(emptySet<String>(), prefs.installedClockFacePacks)
    }

    @Test
    fun `the bundled pack cannot be removed`() = runTest {
        val prefs = com.elmtrackr.app.fake.FakeClockFacePreferences(initialPacks = setOf("NATURE"))
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            prefs, FreeClockFacePackEntitlements(),
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )

        vm.removeClockFacePack(ClockFaceGroup.ESSENTIALS, selected = ClockStyle.CLASSIC)
        advanceUntilIdle()

        assertEquals(setOf("NATURE"), prefs.installedClockFacePacks)
    }

    // ── Buying a pack ─────────────────────────────────────────────────────────

    @Test
    fun `a completed purchase is reported to the user`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore()
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        store.emit(
            com.elmtrackr.app.billing.PackPurchaseEvent.Purchased(setOf(ClockFaceGroup.NATURE)),
        )
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.settings_pack_purchased), vm.packPurchaseFeedback.value)
    }

    /**
     * Backing out of Play's sheet is not a failure and gets no message. The user
     * pressed back; the app narrating that to them is noise, and an error-shaped
     * snackbar would suggest something went wrong.
     */
    @Test
    fun `a cancelled purchase says nothing`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore()
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        store.emit(com.elmtrackr.app.billing.PackPurchaseEvent.Cancelled)
        advanceUntilIdle()

        assertEquals(null, vm.packPurchaseFeedback.value)
    }

    // ── Restoring ─────────────────────────────────────────────────────────────

    /**
     * A restore that recovers something names it, on the same strip a purchase
     * uses. "Your earlier purchase was restored" does not say *what*, and the
     * user pressed Restore because they were looking for a particular pack.
     */
    @Test
    fun `a restored pack is named and reported`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore()
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        store.emit(
            com.elmtrackr.app.billing.PackPurchaseEvent.Restored(setOf(ClockFaceGroup.PAYDAY)),
        )
        advanceUntilIdle()

        assertEquals(setOf(ClockFaceGroup.PAYDAY), vm.justUnlockedPacks.value)
        assertEquals(
            UiText.Res(R.string.settings_pack_purchase_restored),
            vm.packPurchaseFeedback.value,
        )
    }

    /**
     * The usual outcome, and the one that used to look like a broken button:
     * the refresh runs, finds nothing missing, and says nothing at all.
     */
    @Test
    fun `a restore with nothing to recover still answers`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore().apply {
            refreshResult = com.elmtrackr.app.billing.PackRestoreResult.NothingRestored
        }
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        vm.restoreClockFacePacks()
        advanceUntilIdle()

        assertEquals(1, store.refreshCount)
        assertEquals(
            UiText.Res(R.string.settings_pack_restore_nothing),
            vm.packPurchaseFeedback.value,
        )
    }

    /**
     * "Could not ask Play" and "asked Play, nothing missing" are different
     * answers. Telling a user their purchases are up to date when the query
     * never completed would send them to support with the wrong problem.
     */
    @Test
    fun `a restore that could not reach Play says so`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore().apply {
            refreshResult = com.elmtrackr.app.billing.PackRestoreResult.Unavailable
        }
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        vm.restoreClockFacePacks()
        advanceUntilIdle()

        assertEquals(
            UiText.Res(R.string.settings_pack_restore_unavailable),
            vm.packPurchaseFeedback.value,
        )
    }

    /**
     * A restore that recovered packs is reported by the event, so the button
     * stays quiet rather than talking over it with a second snackbar.
     */
    @Test
    fun `a successful restore leaves the message to the event`() = runTest {
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore().apply {
            refreshResult = com.elmtrackr.app.billing.PackRestoreResult.Restored(
                setOf(ClockFaceGroup.PAYDAY),
            )
        }
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        vm.restoreClockFacePacks()
        advanceUntilIdle()

        assertEquals(null, vm.packPurchaseFeedback.value)
    }

    /**
     * The button reports it is working, and cannot be made to queue queries.
     *
     * Play is allowed twenty seconds to answer, so a restore really can sit
     * in flight long enough for an impatient second tap.
     */
    @Test
    fun `restoring is flagged while it runs and cannot be started twice`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = com.elmtrackr.app.fake.FakeClockFacePackStore().apply { refreshGate = gate }
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            clockFacePrefs, FreeClockFacePackEntitlements(), store,
        )
        advanceUntilIdle()

        vm.restoreClockFacePacks()
        advanceUntilIdle()
        assertTrue(vm.isRestoringPacks.value)

        vm.restoreClockFacePacks()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, store.refreshCount)
        assertEquals(false, vm.isRestoringPacks.value)
    }

    @Test
    fun `stored packs reach the state`() = runTest {
        val prefs = com.elmtrackr.app.fake.FakeClockFacePreferences(initialPacks = setOf("NATURE"))
        val vm = SettingsViewModel(
            repo, authRepo, compensationRepo, themeStore, syncRepo, syncTrigger, appLockPrefs,
            prefs, FreeClockFacePackEntitlements(),
            com.elmtrackr.app.fake.FakeClockFacePackStore(),
        )
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        assertEquals(
            setOf(ClockFaceGroup.NATURE),
            states.filterIsInstance<SettingsUiState.Ready>().last().storedClockFacePacks,
        )
        job.cancel()
    }
}
