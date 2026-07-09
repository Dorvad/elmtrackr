package com.elmtrackr.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.monitoring.CrashReporting
import com.elmtrackr.app.startup.AppStartupCoordinator
import dagger.hilt.android.HiltAndroidApp
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
        CrashReporting.startIfConsented(this)
        super.onCreate()
        mainHandler.post { appStartup.onCreate(this) }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
