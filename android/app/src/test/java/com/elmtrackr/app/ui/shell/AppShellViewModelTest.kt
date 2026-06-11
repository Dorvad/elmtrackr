package com.elmtrackr.app.ui.shell

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val onboardingFlow = MutableStateFlow(false)

    private fun buildVm() = AppShellViewModel(authRepo, onboardingFlow)

    private fun testProfile() = Profile(
        id = "user-1",
        email = "user@example.com",
        fullName = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `not configured + onboarding done → Main`() = runTest {
        authRepo.configured = false
        onboardingFlow.value = true

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Main, states.last())
        job.cancel()
    }

    @Test
    fun `not configured + onboarding not done → Onboarding`() = runTest {
        authRepo.configured = false
        onboardingFlow.value = false

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Onboarding, states.last())
        job.cancel()
    }

    @Test
    fun `configured + signed out → Auth`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(null)
        onboardingFlow.value = false

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }

    @Test
    fun `configured + signed in + onboarding done → Main`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        onboardingFlow.value = true

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Main, states.last())
        job.cancel()
    }

    @Test
    fun `configured + signed in + onboarding not done → Onboarding`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        onboardingFlow.value = false

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Onboarding, states.last())
        job.cancel()
    }

    @Test
    fun `sign out while onboarding done → Auth`() = runTest {
        authRepo.configured = true
        authRepo.setProfile(testProfile())
        onboardingFlow.value = true

        val vm = buildVm()
        val states = mutableListOf<AppNavState>()
        val job = launch { vm.navState.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(AppNavState.Main, states.last())

        authRepo.setProfile(null)
        advanceUntilIdle()

        assertEquals(AppNavState.Auth, states.last())
        job.cancel()
    }
}
