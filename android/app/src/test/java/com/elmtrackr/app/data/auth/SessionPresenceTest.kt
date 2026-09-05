package com.elmtrackr.app.data.auth

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Who is signed in, per supabase-kt session status.
 *
 * This is one `when` expression and it is worth a test file of its own, because
 * getting one arm of it wrong signed people out of a working session. The
 * reported symptom was "it logged me out when I ended a shift": clocking out
 * schedules a sync, the sync is the request that finds the access token expired,
 * and the end of a shift is when someone walks out of the building and off the
 * wifi — so the refresh failed on the network, and a state the library
 * documents as *retrying* was read as *signed out*.
 */
class SessionPresenceTest {

    /**
     * The whole bug, as one assertion.
     *
     * supabase-kt raises this when it could not reach the auth server, logs
     * "Couldn't reach Supabase … Retrying in …", keeps the session on the device
     * and tries again. Nothing about it says the user is gone.
     *
     * The classification is on the status, not on the cause, so the other cause —
     * `InternalServerError`, a 5xx the library also retries — is covered by the
     * same arm. It is not asserted separately only because constructing one needs
     * a ktor `HttpResponse`, which would be a test about ktor.
     */
    @Test
    fun `a refresh that could not reach the server keeps the user`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("offline")))

        assertEquals(SessionPresence.RETAINED, status.presence())
    }

    /**
     * The real sign-out, both ways in: the user pressed the button, or the
     * server rejected the refresh token and the library cleared the session.
     */
    @Test
    fun `not authenticated is signed out however it was reached`() {
        assertEquals(
            SessionPresence.SIGNED_OUT,
            SessionStatus.NotAuthenticated(isSignOut = true).presence(),
        )
        assertEquals(
            SessionPresence.SIGNED_OUT,
            SessionStatus.NotAuthenticated(isSignOut = false).presence(),
        )
    }

    /**
     * Start-up, before the stored session has been read. Callers filter this out
     * before rendering; classifying it as signed out would flash the login
     * screen on every cold start.
     */
    @Test
    fun `initializing is not a sign-out`() {
        assertEquals(SessionPresence.RETAINED, SessionStatus.Initializing.presence())
    }
}
