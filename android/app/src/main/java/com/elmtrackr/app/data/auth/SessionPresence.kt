package com.elmtrackr.app.data.auth

import io.github.jan.supabase.auth.status.SessionStatus

/**
 * What one supabase-kt session status means for "is anyone signed in".
 *
 * Three answers, not two, because the library has three kinds of state and
 * collapsing them into a boolean is what produced the bug this exists to
 * prevent: a status that means *we could not ask* was read as *the answer was
 * no*, and a signed-in user was sent to the login screen because their phone
 * had dropped off the wifi.
 */
enum class SessionPresence {

    /** A live session. The status names the user. */
    SIGNED_IN,

    /**
     * No answer yet, and the session is still on the device.
     *
     * The caller should go on serving whoever was last signed in. Nothing about
     * this state says the user is gone, and supabase-kt is already retrying —
     * its own logs read "Retrying in …".
     */
    RETAINED,

    /** The session is over: signed out, or a refresh the server rejected. */
    SIGNED_OUT,
}

/**
 * Classifies [SessionStatus], exhaustively and on purpose.
 *
 * No `else` branch. `SessionStatus` is a sealed interface, so a state added by a
 * future supabase-kt breaks this build instead of falling into a default — and a
 * default is precisely how [SessionStatus.RefreshFailure] came to be treated as a
 * sign-out.
 *
 * The two non-obvious cases, both meaning "no answer" rather than "no":
 *
 *  - [SessionStatus.RefreshFailure] is raised for a network error or a 5xx from
 *    the auth server, and the library retries on a delay. A refresh the server
 *    *rejects* never lands here: that path clears the session and arrives as
 *    [SessionStatus.NotAuthenticated].
 *  - [SessionStatus.Initializing] is the session being read off disk at start-up.
 *    Callers that must not render early filter it out before asking; classifying
 *    it as signed out would flash the login screen on every cold start.
 */
fun SessionStatus.presence(): SessionPresence = when (this) {
    is SessionStatus.Authenticated -> SessionPresence.SIGNED_IN
    is SessionStatus.RefreshFailure -> SessionPresence.RETAINED
    is SessionStatus.Initializing -> SessionPresence.RETAINED
    is SessionStatus.NotAuthenticated -> SessionPresence.SIGNED_OUT
}
