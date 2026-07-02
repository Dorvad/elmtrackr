package com.elmtrackr.app.startup

import android.app.Application
import com.elmtrackr.app.BuildConfig
import io.sentry.android.core.SentryAndroid

object SentryBootstrap {

    fun install(application: Application) {
        SentryAndroid.init(application) { options ->
            val dsn = BuildConfig.SENTRY_DSN
            if (dsn.isBlank()) {
                options.isEnabled = false
            } else {
                options.dsn = dsn
            }
        }
    }
}
