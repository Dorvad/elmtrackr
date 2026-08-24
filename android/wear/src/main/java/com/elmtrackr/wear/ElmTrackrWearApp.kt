package com.elmtrackr.wear

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.elmtrackr.wear.sync.WearActionClient
import com.elmtrackr.wear.sync.WearStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ElmTrackrWearApp : Application() {

    // SupervisorJob keeps one failed child from cancelling its siblings, but it
    // does NOT stop an unhandled failure from reaching the thread's default
    // handler — which on Android means the process dies. Everything this scope
    // runs is background upkeep (cache reads, data-layer round trips, tile
    // refreshes); none of it is worth a crash dialog on the user's wrist.
    private val applicationScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, throwable ->
                if (throwable is CancellationException) return@CoroutineExceptionHandler
                Log.e(TAG, "Unhandled failure on the watch application scope", throwable)
            },
    )

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

    companion object {
        private const val TAG = "ElmTrackrWearApp"

        /**
         * The watch app's own [Application], or null when this context does not
         * belong to it.
         *
         * Every Wear entry point outside the activity — the tile, the
         * complication provider, the data-layer listener, the refresh worker —
         * used to reach the repository with an unchecked
         * `applicationContext as ElmTrackrWearApp`. Those components are started
         * by the system, sometimes into a process this app did not create
         * (an isolated or restricted context under Robolectric, a test harness,
         * or a store review harness that stubs the application class), and a
         * ClassCastException there is an immediate crash with no useful message.
         * Callers now get null and skip their work instead.
         */
        fun from(context: Context): ElmTrackrWearApp? =
            context.applicationContext as? ElmTrackrWearApp
    }
}
