package com.elmtrackr.wear

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.elmtrackr.wear.monitoring.WearCrashReporting
import com.elmtrackr.wear.sync.WearActionClient
import com.elmtrackr.wear.sync.WearStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
                // Logged and reported. Logging alone is what this module did before,
                // and logcat on a store reviewer's watch is not somewhere anyone can
                // read: three rejections mentioning a crash produced no stack trace
                // between them.
                WearCrashReporting.report(throwable)
            },
    )

    lateinit var wearStateRepository: WearStateRepository
        private set

    lateinit var wearActionClient: WearActionClient
        private set

    override fun onCreate() {
        super.onCreate()
        // First, and before anything else can fail: the crash on the launch path is
        // the one this module keeps being rejected for, so the reporter has to be
        // running by the time that path executes. It reads its consent synchronously
        // from SharedPreferences and swallows its own failures — see
        // [WearCrashReporting].
        WearCrashReporting.startIfConsented(this)
        wearStateRepository = WearStateRepository(this)
        wearActionClient = WearActionClient(this, wearStateRepository)
        applicationScope.launch {
            wearStateRepository.bootstrap()
        }
        // The watch has no settings screen, so the user's choice about crash
        // reporting reaches it here, in every snapshot the phone pushes.
        applicationScope.launch {
            wearStateRepository.snapshot
                .map { it.crashReportingEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    WearCrashReporting.applyPhoneConsent(this@ElmTrackrWearApp, enabled)
                }
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
