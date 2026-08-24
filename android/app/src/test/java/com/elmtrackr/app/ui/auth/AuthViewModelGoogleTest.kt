package com.elmtrackr.app.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeGoogleSignInClient
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The four Google outcomes, each of which the user experiences differently.
 *
 * Robolectric only for the Context: Credential Manager needs an Activity, so the
 * ViewModel takes one, and there is no such thing off a device. Everything being
 * asserted is plain ViewModel logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuthViewModelGoogleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeAuthRepository()
    private val googleClient = FakeGoogleSignInClient()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildVm() = AuthViewModel(repo, googleClient)

    private fun signedOut(vm: AuthViewModel) = vm.uiState.value as AuthUiState.SignedOut

    @Test
    fun `a token is exchanged with the raw nonce and signs the user in`() = runTest {
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals("id-token", repo.lastGoogleIdToken)
        // The raw nonce, never the hash. Sending the hash is the failure mode
        // this assertion exists to catch.
        assertEquals("raw-nonce", repo.lastGoogleRawNonce)
        assertTrue(vm.uiState.value.toString(), vm.uiState.value is AuthUiState.SignedIn)
        job.cancel()
    }

    @Test
    fun `dismissing the sheet leaves no error and does not reach the repository`() = runTest {
        googleClient.nextResult = GoogleIdTokenResult.Cancelled
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertNull(signedOut(vm).errorMessage)
        assertNull(repo.lastGoogleIdToken)
        assertEquals(0, repo.browserSignInStarted)
        job.cancel()
    }

    @Test
    fun `no account on the device falls back to the browser`() = runTest {
        googleClient.nextResult = GoogleIdTokenResult.NoAccountOnDevice
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(1, repo.browserSignInStarted)
        // The browser is now on top; nothing has gone wrong yet, so nothing is said.
        assertNull(signedOut(vm).errorMessage)
        job.cancel()
    }

    @Test
    fun `a missing credential provider falls back to the browser`() = runTest {
        googleClient.nextResult = GoogleIdTokenResult.ProviderUnavailable
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(1, repo.browserSignInStarted)
        job.cancel()
    }

    @Test
    fun `a failure is shown and is not quietly retried in the browser`() = runTest {
        googleClient.nextResult = GoogleIdTokenResult.Failure(IllegalStateException("bad client id"))
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(
            UiText.Res(R.string.auth_error_google_sign_in),
            signedOut(vm).errorMessage,
        )
        assertEquals(0, repo.browserSignInStarted)
        job.cancel()
    }

    @Test
    fun `a repository refusal surfaces its own message`() = runTest {
        repo.googleSignInResult = AuthResult.Error(UiText.Res(R.string.auth_error_network))
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.auth_error_network), signedOut(vm).errorMessage)
        job.cancel()
    }

    @Test
    fun `a failed browser fallback reports rather than ending in silence`() = runTest {
        googleClient.nextResult = GoogleIdTokenResult.NoAccountOnDevice
        repo.googleBrowserResult = AuthResult.Error(UiText.Res(R.string.auth_error_network))
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.auth_error_network), signedOut(vm).errorMessage)
        job.cancel()
    }

    @Test
    fun `the button is offered only when the build carries a client id`() = runTest {
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        assertTrue(signedOut(vm).googleSignInAvailable)
        job.cancel()

        val unconfigured = AuthViewModel(repo, FakeGoogleSignInClient(isConfigured = false))
        val job2 = launch { unconfigured.uiState.collect { } }
        advanceUntilIdle()
        assertEquals(false, signedOut(unconfigured).googleSignInAvailable)
        job2.cancel()
    }

    @Test
    fun `the email form stays usable while the Google sheet is up`() = runTest {
        googleClient.holdUntil = CompletableDeferred()
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.signInWithGoogle(context)
        advanceUntilIdle()

        // The sheet is up. The Google button is busy; the email form must not be,
        // or the whole screen reads as frozen while the user picks an account.
        val midFlight = signedOut(vm)
        assertEquals(true, midFlight.isGoogleLoading)
        assertEquals(false, midFlight.isLoading)

        googleClient.holdUntil?.complete(Unit)
        advanceUntilIdle()
        job.cancel()
    }
}
