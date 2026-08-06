package com.elmtrackr.app.ui.common

import androidx.annotation.StringRes
import com.elmtrackr.app.R
import com.elmtrackr.app.data.remote.RemoteSyncErrors
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.monitoring.CrashReporting
import java.io.IOException

/**
 * Turns a failure into something worth showing a user, and sends the technical
 * detail where technical detail belongs.
 *
 * The pattern this replaces was `error.message?.let { UiText.Raw(it) } ?:
 * UiText.Res(fallback)` — which reads as "prefer the specific message" and
 * behaves as "always show the exception". Those messages are PostgREST error
 * bodies, Ktor socket errors and Java IO messages: untranslated, unactionable,
 * and occasionally revealing a column or constraint name. The localized fallback
 * sitting right beside them was almost never reached.
 *
 * So the exception text is never displayed. What the user gets is either a
 * recognised cause — offline, expired session, a server refusal, a full disk —
 * or the caller's own fallback, all of which are translated. What the exception
 * said is reported to crash reporting instead, where it can be read by someone
 * who can act on it.
 */
object UserFacingError {

    /**
     * @param fallback the caller's own message for "this operation failed",
     *   used when the cause is not one of the recognised ones. Callers pass
     *   something specific to what the user was doing — that is far more useful
     *   than any generic sentence this could invent.
     */
    fun message(error: Throwable, @StringRes fallback: Int): UiText {
        report(error)
        return UiText.Res(classify(error) ?: fallback)
    }

    /**
     * The recognised causes, or null to leave it to the caller's fallback.
     *
     * Deliberately conservative. A wrong-but-confident explanation ("you're
     * offline") is worse than a vague one, because it sends the user off to
     * check something that was never the problem.
     */
    @StringRes
    private fun classify(error: Throwable): Int? {
        if (RemoteSyncErrors.isAuthExpired(error)) return R.string.error_session_expired

        val chain = generateSequence(error) { it.cause }.take(CAUSE_DEPTH).toList()

        if (chain.any { it.isOutOfSpace() }) return R.string.error_storage_full
        if (chain.any { it is SecurityException }) return R.string.error_permission
        if (chain.any { it.isOffline() }) return R.string.error_offline
        // After the offline check: an IOException that reached here is a
        // transport failure of some other kind, which reads as a server problem
        // from where the user is sitting.
        if (chain.any { it is IOException }) return R.string.error_server
        if (chain.any { it.mentionsServerFailure() }) return R.string.error_server

        return null
    }

    private fun Throwable.isOffline(): Boolean {
        val className = this::class.qualifiedName.orEmpty()
        if (className.endsWith("UnknownHostException") ||
            className.endsWith("ConnectException") ||
            className.endsWith("SocketTimeoutException") ||
            className.endsWith("NoRouteToHostException")
        ) {
            return true
        }
        val message = message.orEmpty()
        return message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("Failed to connect", ignoreCase = true) ||
            message.contains("Network is unreachable", ignoreCase = true)
    }

    private fun Throwable.isOutOfSpace(): Boolean =
        message.orEmpty().contains("ENOSPC", ignoreCase = true) ||
            message.orEmpty().contains("No space left on device", ignoreCase = true)

    /** PostgREST and PostgreSQL codes, which arrive as text in the message. */
    private fun Throwable.mentionsServerFailure(): Boolean {
        val message = message.orEmpty()
        return message.contains("PGRST", ignoreCase = true) ||
            message.contains("HTTP 5", ignoreCase = false) ||
            message.contains("Internal Server Error", ignoreCase = true)
    }

    /**
     * Reported rather than logged to Logcat: the whole point is that this detail
     * has to reach someone who can act on it, and by the time a user reports "it
     * said something went wrong" their Logcat is long gone.
     */
    private fun report(error: Throwable) {
        CrashReporting.report(error)
    }

    private const val CAUSE_DEPTH = 5
}
