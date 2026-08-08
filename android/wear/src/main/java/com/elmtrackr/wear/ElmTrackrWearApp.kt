package com.elmtrackr.wear

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
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

    /**
     * Tile punches run here because the NoDisplay trampoline activity must
     * finish immediately. Feedback is a system haptic (the tile has no UI of
     * its own); the confirmation overlay still appears if the app is open.
     */
    fun punchFromTile(isPunchIn: Boolean) {
        applicationScope.launch {
            val result = if (isPunchIn) wearActionClient.punchIn() else wearActionClient.punchOut()
            vibrate(success = result.success)
            if (result.success) {
                wearStateRepository.showConfirmation(
                    getString(if (isPunchIn) R.string.confirmed_in else R.string.confirmed_out),
                )
            } else {
                // A failed tile punch usually means the tile is stale (the phone
                // already ended or started the shift) — re-pull so the next
                // render shows the real state instead of failing again.
                wearActionClient.requestRefreshFromPhone()
            }
        }
    }

    private fun vibrate(success: Boolean) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val effect = if (success) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
