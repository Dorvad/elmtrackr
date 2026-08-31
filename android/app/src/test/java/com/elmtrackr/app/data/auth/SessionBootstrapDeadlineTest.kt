package com.elmtrackr.app.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deadline on the session bootstrap, and why it is shaped the way it is.
 *
 * `AppShellViewModel.navState` holds the app on `AppNavState.Loading` while a
 * signed-in user has no local settings row and `sessionBootstrapComplete` is
 * still false. Both bootstrap steps were unbounded remote calls, so a socket
 * that accepted and never answered — captive portal, black-holed connection —
 * left that flag false for the life of the process: an app that never opens.
 *
 * The subtle part is the catch ordering, which is what these tests pin.
 * `TimeoutCancellationException` extends `CancellationException`, so catching
 * cancellation first would rethrow the timeout, skip the line that opens the
 * gate, and reproduce the exact hang the deadline was added to prevent.
 */
class SessionBootstrapDeadlineTest {

    @Test
    fun `a step that never returns is abandoned at the deadline rather than hanging`() = runTest {
        var completed: Boolean? = null

        // awaitCancellation, not delay: this is a call that is waiting on an
        // answer which never comes, which is the failure being reproduced.
        completed = runBootstrapStep(timeoutMillis = 45_000L) { awaitCancellation() }

        assertEquals(false, completed)
    }

    /** The caller must reach the line after the step. That line opens the gate. */
    @Test
    fun `the caller carries on past a stalled step`() = runTest {
        var gateOpened = false

        runBootstrapStep(timeoutMillis = 1_000L) { awaitCancellation() }
        gateOpened = true

        assertTrue(gateOpened)
    }

    @Test
    fun `a failing step is swallowed like the best-effort call it is`() = runTest {
        val completed = runBootstrapStep { error("sync blew up") }

        assertFalse(completed)
    }

    @Test
    fun `a step that finishes reports success`() = runTest {
        var ran = false

        val completed = runBootstrapStep { ran = true }

        assertTrue(ran)
        assertTrue(completed)
    }

    /**
     * Sign-out and `resetSession` cancel the bootstrap job, and that must still
     * tear the step down: opening the gate for a session that is going away
     * would navigate a signed-out user into the app.
     */
    @Test
    fun `cancellation from outside still propagates`() = runTest {
        var reachedPastStep = false

        val job = launch {
            runBootstrapStep { awaitCancellation() }
            reachedPastStep = true
        }
        yield()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(reachedPastStep)
    }

    @Test
    fun `the shipped deadline is a human wait, not a network timeout`() {
        assertTrue(
            "expected a deadline long enough for a slow first sync",
            REMOTE_BOOTSTRAP_TIMEOUT_MILLIS >= 30_000L,
        )
        assertTrue(
            "expected a deadline short enough to be a wait rather than a dead end",
            REMOTE_BOOTSTRAP_TIMEOUT_MILLIS <= 60_000L,
        )
    }
}
