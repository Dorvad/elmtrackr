package com.elmtrackr.app.monitoring

import android.content.Context
import com.elmtrackr.app.BuildConfig
import com.elmtrackr.wear.sync.CrashReportScrubber
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import io.sentry.exception.SentryHttpClientException

/**
 * Crash reporting (Sentry), gated on two things: a DSN compiled into the build
 * and the user's consent. Consent lives in plain SharedPreferences because it
 * must be readable synchronously in Application.onCreate, before anything can
 * crash.
 */
object CrashReporting {

    private const val PREFS = "crash_reporting"
    private const val KEY_ENABLED = "enabled"

    /** True when this build was compiled with a Sentry DSN. */
    fun isAvailable(): Boolean = BuildConfig.SENTRY_DSN.isNotBlank()

    fun isEnabledByUser(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    /** Called from Application.onCreate. */
    fun startIfConsented(context: Context) {
        if (isAvailable() && isEnabledByUser(context)) start(context)
    }

    /** Called from the settings toggle; takes effect immediately. */
    fun setEnabledByUser(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        when {
            !isAvailable() -> Unit
            enabled -> start(context.applicationContext)
            else -> Sentry.close()
        }
    }

    /**
     * Reports a throwable that was handled rather than fatal.
     *
     * Used by the application scope's [kotlinx.coroutines.CoroutineExceptionHandler]:
     * background work that fails should be visible without taking the process
     * down. Safe to call when Sentry was never started — the SDK no-ops, and the
     * `runCatching` guarantees a reporting failure cannot itself become the
     * crash we were trying to avoid.
     */
    fun report(throwable: Throwable) {
        runCatching { Sentry.captureException(throwable) }
    }

    private fun start(context: Context) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "com.elmtrackr.app@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.tracesSampleRate = 0.0
            options.isAttachStacktrace = true
            // Sessions are the one thing here that transmits without a crash: one
            // envelope per foreground and background, carrying the release, the device
            // and Sentry's installation id. That is what crash-free-rate is computed
            // from, and it is why the data safety form declares Device or other IDs.
            // See docs/play-data-safety.md.
            options.isEnableAutoSessionTracking = true
            // Three ways the contents of the user's screen could become an attachment.
            // All three are off by default in this SDK version; they are set anyway
            // because a default is a decision someone else gets to change, and this one
            // would change what the data safety declaration has to say without a line
            // of this app's code moving.
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            // Breadcrumbs are scrubbed where they are recorded, not only where they are
            // sent: an NDK crash is written straight to the outbox and uploaded on the
            // next launch, and that path never reaches setBeforeSend.
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                runCatching { CrashReportScrubber.scrub(breadcrumb) }
                breadcrumb
            }
            // Last gate before an event leaves the device. See CrashReportScrubber for
            // what it removes and why. Wrapped in runCatching because a throw here
            // happens inside the SDK's own send path: losing a report is a bad
            // outcome, losing the process while reporting a crash is a worse one.
            options.setBeforeSend { event, _ ->
                if (isFailedHttpResponse(event)) return@setBeforeSend null
                runCatching { CrashReportScrubber.scrub(event) }
                event
            }
        }
    }

    /**
     * True for the event Sentry's OkHttp interceptor raises by itself whenever a
     * response comes back 5xx ("HTTP Client Error with status code: 504").
     *
     * The Gradle plugin auto-installs `sentry-okhttp` because okhttp arrives under
     * Ktor, and the interceptor it wires in is built with its defaults: capture failed
     * requests, statuses 500–599, every host. In 8.50.1 that switch is a constructor
     * argument of `SentryOkHttpInterceptor`, not a field of `SentryOptions`, so an app
     * that never constructs the interceptor cannot turn it off. This is where it is
     * turned off instead.
     *
     * Dropped because none of these events describe a defect in this app:
     *
     * - A 5xx from Supabase is the server or the network path asking for later. Sync
     *   already treats it that way (`RemoteSyncErrors.isTransient`), keeps the cursor
     *   where it was and retries with backoff. The event says which request failed,
     *   not which line of this app is wrong, so there is nothing in it to fix.
     * - During a backend incident every active install reports one per failed
     *   request, which buries real crashes and spends the event quota on noise.
     * - It is a transmission without a crash. The comment on session tracking above,
     *   and `docs/play-data-safety.md`, say sessions are the only thing that leaves
     *   without one; this keeps that true.
     *
     * HTTP breadcrumbs are not affected. They still ride along, scrubbed, on any real
     * crash, so the requests that preceded it remain visible.
     */
    internal fun isFailedHttpResponse(event: SentryEvent): Boolean =
        event.throwable is SentryHttpClientException
}
