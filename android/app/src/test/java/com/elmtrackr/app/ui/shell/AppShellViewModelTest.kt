package com.elmtrackr.app.ui.shell

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepo = FakeAuthRepository()
    private val settingsRepo = FakeSettingsRepository()

    private fun buildVm() = AppShellViewModel(authRepo, settingsRepo)

    private fun setOnboarding(completed: Boolean) {
        settingsRepo.setSettings(
            UserSettings(
                id = "settings-1",
                userId = "user-1",
                onboardingCompleted = completed,
            ),
        )
    }

    private fun testProfile() = Profile(
        id = "user-1",
        email = "user@example.com",
        fullName = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // ---- No config → always Auth ----

    @Test
    fun `no config + no onboarding → Auth (not-configured)`() = runTest {
        authRepo.configured = false

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    @Test
    fun `no config + onboarding previously completed → still Auth, not Main`() = runTest {
        // Previously completed onboarding must NOT bypass the auth gate when unconfigured.
        authRepo.configured = false

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    // ---- Configured, no session → Auth ----

    @Test
    fun `configured + no session → Auth`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(null)

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    // ---- Configured, session present ----

    @Test
    fun `session exists + onboarding incomplete → Onboarding`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        setOnboarding(false)

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Onboarding, states.last())
        job.cancel()
    }

    @Test
    fun `session exists + onboarding complete → Main`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        setOnboarding(true)

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Main, states.last())
        job.cancel()
    }

    // ---- Sign out ----

    @Test
    fun `sign out returns to Auth and does not route back to Onboarding`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        setOnboarding(true)

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Main, states.last())

        // Sign out clears the profile; onboarding flag is still true
        authRepo.setProfile(null)
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    @Test
    fun `sign out when onboarding incomplete also returns to Auth`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        setOnboarding(false)

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Onboarding, states.last())

        authRepo.setProfile(null)
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    // ---- Initial state ----

    @Test
    fun `initial state is Loading before first emission`() {
        val vm = buildVm()
        assertEquals(AppNavState.Loading, vm.navState.value)
    }
}
