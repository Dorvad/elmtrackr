package com.elmtrackr.app.monitoring

import android.content.Context
import com.elmtrackr.app.BuildConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

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
            options.isEnableAutoSessionTracking = true
        }
    }
}
