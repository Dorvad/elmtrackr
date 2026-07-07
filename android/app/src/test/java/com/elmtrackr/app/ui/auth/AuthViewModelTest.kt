package com.elmtrackr.app.ui.auth

import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeAuthRepository()

    private fun buildVm() = AuthViewModel(repo)

    @Test
    fun `initial state is Loading`() {
        assertEquals(AuthUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `NotConfigured state when repo is not configured`() = runTest {
        repo.configured = false
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        advanceUntilIdle()

        assertTrue(states.any { it is AuthUiState.NotConfigured })
        job.cancel()
    }

    @Test
    fun `SignedOut state when no profile`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setProfile(null)
        advanceUntilIdle()

        assertTrue(states.any { it is AuthUiState.SignedOut })
        job.cancel()
    }

    @Test
    fun `signIn success leads to SignedIn state`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.signIn("test@example.com", "password123")
        advanceUntilIdle()

        val signedIn = states.filterIsInstance<AuthUiState.SignedIn>().lastOrNull()
        assertNotNull(signedIn)
        assertEquals("test@example.com", signedIn!!.profile.email)
        job.cancel()
    }

    @Test
    fun `signIn keeps loading until profile propagates`() = runTest {
        repo.signInSetsProfile = false
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        runCurrent()

        vm.signIn("test@example.com", "password123")
        runCurrent()

        val signedOut = states.filterIsInstance<AuthUiState.SignedOut>().last()
        assertTrue(signedOut.isLoading)

        repo.setProfile(
            Profile("user-1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH),
        )
        advanceUntilIdle()

        assertTrue(states.last() is AuthUiState.SignedIn)
        job.cancel()
    }

    @Test
    fun `signIn stops loading if profile never propagates`() = runTest {
        repo.signInSetsProfile = false
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        runCurrent()

        vm.signIn("test@example.com", "password123")
        runCurrent()
        advanceTimeBy(11_000)
        runCurrent()

        val signedOut = states.filterIsInstance<AuthUiState.SignedOut>().last()
        assertEquals(false, signedOut.isLoading)
        job.cancel()
    }

    @Test
    fun `signIn error populates errorMessage in SignedOut state`() = runTest {
        repo.signInResult = AuthResult.Error(UiText.Raw("Invalid credentials"))
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.signIn("test@example.com", "wrongpassword")
        advanceUntilIdle()

        val signedOut = states.filterIsInstance<AuthUiState.SignedOut>().lastOrNull()
        assertEquals(UiText.Raw("Invalid credentials"), signedOut?.errorMessage)
        job.cancel()
    }

    @Test
    fun `signOut clears profile and returns to SignedOut`() = runTest {
        repo.setProfile(
            Profile("user-1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH),
        )
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertTrue(states.any { it is AuthUiState.SignedOut })
        assertNull(states.filterIsInstance<AuthUiState.SignedIn>().lastOrNull()?.let {
            repo.getCurrentProfile()
        })
        job.cancel()
    }

    @Test
    fun `resetPassword success emits PasswordResetSent`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.resetPassword("test@example.com")
        advanceUntilIdle()

        assertTrue(states.any { it is AuthUiState.PasswordResetSent })
        job.cancel()
    }

    @Test
    fun `dismissPasswordReset returns to SignedOut`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.resetPassword("test@example.com")
        advanceUntilIdle()

        vm.dismissPasswordReset()
        advanceUntilIdle()

        assertTrue(states.last() is AuthUiState.SignedOut)
        job.cancel()
    }

    @Test
    fun `signUp success leads to SignedIn state`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.signUp("new@example.com", "password123")
        advanceUntilIdle()

        val signedIn = states.filterIsInstance<AuthUiState.SignedIn>().lastOrNull()
        assertNotNull(signedIn)
        assertEquals("new@example.com", signedIn!!.profile.email)
        job.cancel()
    }

    @Test
    fun `resetPassword error populates errorMessage in SignedOut`() = runTest {
        repo.resetPasswordResult = AuthResult.Error(UiText.Raw("User not found"))
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.resetPassword("notfound@example.com")
        advanceUntilIdle()

        val signedOut = states.filterIsInstance<AuthUiState.SignedOut>().lastOrNull()
        assertEquals(UiText.Raw("User not found"), signedOut?.errorMessage)
        job.cancel()
    }

    @Test
    fun `password recovery required emits PasswordRecovery`() = runTest {
        repo.setProfile(
            Profile("user-1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH),
        )
        repo.setPasswordRecoveryRequired(true)
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        assertTrue(states.last() is AuthUiState.PasswordRecovery)
        job.cancel()
    }

    @Test
    fun `updatePassword success clears recovery state`() = runTest {
        repo.setProfile(
            Profile("user-1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH),
        )
        repo.setPasswordRecoveryRequired(true)
        val vm = buildVm()
        val states = mutableListOf<AuthUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.updatePassword("newpassword123")
        advanceUntilIdle()

        assertTrue(states.last() is AuthUiState.SignedIn)
        job.cancel()
    }
}
