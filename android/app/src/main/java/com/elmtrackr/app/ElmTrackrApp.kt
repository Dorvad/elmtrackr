package com.elmtrackr.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.startup.AppStartupCoordinator
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject

@HiltAndroidApp
class ElmTrackrApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appStartup: AppStartupCoordinator

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ElmTrackrDatabase.preWarm(this)
    }

    override fun onCreate() {
        initSentry()
        super.onCreate()
        mainHandler.post { appStartup.onCreate(this) }
    }

    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "com.elmtrackr.app@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.tracesSampleRate = 0.0
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = true
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
