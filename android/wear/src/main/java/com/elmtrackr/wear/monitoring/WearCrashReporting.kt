package com.elmtrackr.wear.monitoring

import android.content.Context
import android.util.Log
import com.elmtrackr.wear.BuildConfig
import com.elmtrackr.wear.runCatchingCancellable
import com.elmtrackr.wear.sync.CrashReportScrubber
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Crash reporting for the watch app.
 *
 * ### Why this module has it at all
 *
 * Play has rejected the watch artifact three times for the same family of problem —
 * "does not install or launch without crashing", then "functionality not working as
 * described … your app crashed when testing" — and not one of those rejections
 * produced a stack trace. `wear-play-resubmission-2026-08.md` §3 records the result:
 * the second attempt removed the crash paths a reading of the code could find, which
 * is not the same as fixing the crash, and the rejection came back. The phone module
 * has had Sentry throughout; this module had nothing, so a crash on a reviewer's watch
 * left no evidence anywhere. That is the gap this closes. It does not fix the crash —
 * it makes the next one answerable.
 *
 * ### Consent
 *
 * The watch ships no settings screen, so the consent the user gave or withheld in the
 * phone app arrives over the data layer in [com.elmtrackr.wear.sync.WearShiftSnapshot]
 * and is cached here in plain `SharedPreferences` — plain, because it has to be
 * readable synchronously in `Application.onCreate`, before anything can crash.
 *
 * The cached default is on, which matches the phone's own opt-out default and is also
 * what makes a watch diagnosable before it has ever been paired. Once a snapshot says
 * otherwise, [applyPhoneConsent] persists that and stops the SDK, and the answer
 * survives the next launch.
 *
 * ### Every entry point is guarded
 *
 * A crash reporter that throws while starting, in the `onCreate` of an app being
 * rejected for launch crashes, would be the worst possible way to make this problem
 * worse. So nothing here is allowed to propagate: init, shutdown and capture are each
 * wrapped, and a failure is logged and dropped.
 */
object WearCrashReporting {

    private const val TAG = "WearCrashReporting"
    private const val PREFS = "wear_crash_reporting"
    private const val KEY_ENABLED = "enabled"

    /** True when this build was compiled with a Sentry DSN. */
    fun isAvailable(): Boolean = BuildConfig.SENTRY_DSN.isNotBlank()

    /** The phone's last known answer, defaulting to on. See the class KDoc. */
    fun isEnabled(context: Context): Boolean =
        runCatchingCancellable {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
        }.getOrDefault(true)

    /** Called from `Application.onCreate`. */
    fun startIfConsented(context: Context) {
        if (isAvailable() && isEnabled(context)) start(context)
    }

    /**
     * Applies the consent carried by a snapshot from the phone.
     *
     * Idempotent and cheap enough to call on every snapshot: it only touches the SDK
     * when the answer actually changed.
     */
    fun applyPhoneConsent(context: Context, enabled: Boolean) {
        if (isEnabled(context) == enabled) return
        runCatchingCancellable {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }.onFailure { Log.w(TAG, "Could not persist the crash reporting choice", it) }
        if (!isAvailable()) return
        if (enabled) start(context.applicationContext) else stop()
    }

    /**
     * Reports a throwable that was handled rather than fatal — the watch application
     * scope's failures, which are deliberately survivable but should not be silent.
     *
     * A no-op when the SDK was never started.
     */
    fun report(throwable: Throwable) {
        runCatchingCancellable { Sentry.captureException(throwable) }
            .onFailure { Log.w(TAG, "Could not report a handled failure", it) }
    }

    private fun stop() {
        runCatchingCancellable { Sentry.close() }
            .onFailure { Log.w(TAG, "Could not stop crash reporting", it) }
    }

    private fun start(context: Context) {
        runCatchingCancellable {
            SentryAndroid.init(context) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.environment = if (BuildConfig.DEBUG) "debug" else "release"
                // Deliberately the wear applicationId and not the phone's, even though
                // the two share one. Both artifacts report into the same project, and a
                // release string that did not say which form factor a crash came from
                // would put the watch's crashes in with the phone's — which is exactly
                // the question these reports exist to answer.
                options.release =
                    "com.elmtrackr.wear@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isSendDefaultPii = false
                // No performance tracing on a watch: the reports are wanted for crashes,
                // and spans would cost battery and radio for data nobody is reading.
                options.tracesSampleRate = 0.0
                options.isAttachStacktrace = true
                // Mirrors :app. Session replay is not even on this module's classpath —
                // the `sentry` block above is what keeps it off a wrist — so these are
                // belt and braces against a future dependency change, and cost two
                // field writes at start-up.
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.sessionReplay.sessionSampleRate = 0.0
                options.sessionReplay.onErrorSampleRate = 0.0
                options.setBeforeBreadcrumb { breadcrumb, _ ->
                    runCatchingCancellable { CrashReportScrubber.scrub(breadcrumb) }
                    breadcrumb
                }
                options.setBeforeSend { event, _ ->
                    runCatchingCancellable { CrashReportScrubber.scrub(event) }
                    event
                }
            }
        }.onFailure {
            // The whole point of this class is that the watch app stops dying on the
            // launch path. Dying in the reporter would be beyond ironic.
            Log.e(TAG, "Could not start crash reporting", it)
        }
    }
}
