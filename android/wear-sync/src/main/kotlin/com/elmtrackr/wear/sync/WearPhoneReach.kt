package com.elmtrackr.wear.sync

/** The phone node a watch action should talk to, and how much it is known about it. */
data class WearPhoneTarget(
    val nodeId: String,
    /**
     * True when the node advertises [WearCapabilities.PHONE_APP], i.e. the phone is
     * running ElmTrackr and will answer a punch. False when the watch only knows that
     * *a* phone is connected — it may be a phone without the app, or one on a build
     * older than the capability, so it gets the short budget below.
     */
    val runsElmTrackr: Boolean,
)

/**
 * Picks the phone to talk to and the time budget to give it.
 *
 * Until September 2026 the watch treated every connected node as an ElmTrackr phone
 * and waited the full ten seconds for a punch acknowledgement. A watch paired to a
 * phone that does not have the app installed — which is how a store reviewer tests a
 * standalone watch app — therefore showed a ten-second spinner after every punch, and
 * a tile tap did nothing visible for ten seconds, before falling back to the local
 * punch it could have made immediately.
 *
 * Pure so it can be tested without Play Services; the client maps `Node` objects to
 * [Candidate]s.
 */
object WearPhoneReach {

    data class Candidate(val id: String, val isNearby: Boolean)

    /**
     * A node advertising the phone capability always wins. Otherwise the first
     * connected node is used, flagged as unconfirmed, so a phone on a build that
     * predates the capability still receives punches — just with the short budget.
     */
    fun choose(
        capabilityNodes: List<Candidate>,
        connectedNodes: List<Candidate>,
    ): WearPhoneTarget? {
        prefer(capabilityNodes)?.let { return WearPhoneTarget(it.id, runsElmTrackr = true) }
        return prefer(connectedNodes)?.let { WearPhoneTarget(it.id, runsElmTrackr = false) }
    }

    /**
     * How long to wait for the phone's punch acknowledgement.
     *
     * Ten seconds for a confirmed phone: its punch includes a Room write and a
     * Supabase push, and five seconds produced false "failed" feedback on slow
     * networks — and a retry after a false failure is how duplicate punches happen.
     * A short budget for an unconfirmed node: if it is an older ElmTrackr build the
     * timeout path still checks the phone's snapshot before falling back locally, and
     * if it is a phone without the app no answer is coming at all.
     */
    fun punchResultTimeoutMillis(target: WearPhoneTarget): Long =
        if (target.runsElmTrackr) CONFIRMED_PUNCH_TIMEOUT_MS else UNCONFIRMED_PUNCH_TIMEOUT_MS

    /** How many [REFRESH_POLL_INTERVAL_MS] beats to wait for a snapshot after asking for one. */
    fun refreshPollAttempts(target: WearPhoneTarget): Int =
        if (target.runsElmTrackr) CONFIRMED_REFRESH_POLL_ATTEMPTS else UNCONFIRMED_REFRESH_POLL_ATTEMPTS

    private fun prefer(nodes: List<Candidate>): Candidate? =
        nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()

    const val CONFIRMED_PUNCH_TIMEOUT_MS = 10_000L
    const val UNCONFIRMED_PUNCH_TIMEOUT_MS = 2_500L

    const val REFRESH_POLL_INTERVAL_MS = 250L
    // ~3s: covers a phone DB read plus a data-layer round trip.
    const val CONFIRMED_REFRESH_POLL_ATTEMPTS = 12
    // ~1s: long enough for an older ElmTrackr phone that answers, short enough that a
    // watch next to a phone without the app is not held on every resume.
    const val UNCONFIRMED_REFRESH_POLL_ATTEMPTS = 4
}
