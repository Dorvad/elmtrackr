package com.elmtrackr.wear

import android.app.Application
import com.elmtrackr.wear.sync.WearActionClient
import com.elmtrackr.wear.sync.WearStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ElmTrackrWearApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var wearStateRepository: WearStateRepository
        private set

    lateinit var wearActionClient: WearActionClient
        private set

    override fun onCreate() {
        super.onCreate()
        wearStateRepository = WearStateRepository(this)
        wearActionClient = WearActionClient(this, wearStateRepository)
        applicationScope.launch {
            wearStateRepository.bootstrap()
        }
    }
}
