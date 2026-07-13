package com.elmtrackr.app.update

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch

/**
 * Drives Google Play in-app updates for [activity].
 *
 * On every resume it asks Play whether an update is available and applies
 * [InAppUpdatePolicy] to start the flexible or immediate flow. For flexible
 * updates it watches the install state and invokes [onPrompt] with
 * [InAppUpdatePrompt.RestartReady] once the new version finishes downloading.
 *
 * When Play reports an update but no in-app flow can run, [onPrompt] may
 * receive [InAppUpdatePrompt.PlayStore] (rate-limited) so users still have a
 * path to update.
 *
 * Outside of Google Play (sideloaded debug builds, CI, emulators without Play)
 * the Play task fails or reports "no update available" and every method becomes a
 * safe no-op — the app behaves exactly as it did before.
 */
class InAppUpdateManager(
    private val activity: ComponentActivity,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
    private val promptStore: InAppUpdatePromptStore = InAppUpdatePromptStore(activity),
    private val onPrompt: (InAppUpdatePrompt) -> Unit,
) : DefaultLifecycleObserver {

    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        when (result.resultCode) {
            Activity.RESULT_OK -> Unit
            Activity.RESULT_CANCELED -> {
                flexibleFlowStarted = false
                activity.lifecycleScope.launch {
                    promptStore.recordFlexibleDismissed()
                }
                UpdateDiagnostics.recordUserDismissed()
                Log.d(TAG, "Flexible update dismissed by user.")
            }
            else -> {
                flexibleFlowStarted = false
                UpdateDiagnostics.recordFlowFailed(result.resultCode)
                Log.d(TAG, "Update flow did not complete (resultCode=${result.resultCode}).")
            }
        }
    }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onPrompt(InAppUpdatePrompt.RestartReady)
        }
    }

    private var flexibleFlowStarted = false
    private var listenerRegistered = false

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        checkForUpdate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unregisterListener()
    }

    /** Queries Play and starts the appropriate update flow, if any. */
    fun checkForUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                activity.lifecycleScope.launch {
                    handleUpdateInfo(info)
                }
            }
            .addOnFailureListener { e ->
                UpdateDiagnostics.recordCheck(
                    status = InAppUpdateStatus(
                        updateAvailable = false,
                        flexibleAllowed = false,
                        immediateAllowed = false,
                    ),
                    action = InAppUpdateAction.NONE,
                    flexibleFlowStarted = flexibleFlowStarted,
                )
                Log.d(TAG, "Skipping in-app update check: ${e.message}")
            }
    }

    /** Completes a downloaded flexible update by restarting the app to install it. */
    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
    }

    private suspend fun handleUpdateInfo(info: AppUpdateInfo) {
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            onPrompt(InAppUpdatePrompt.RestartReady)
            return
        }

        val status = info.toStatus()
        val action = InAppUpdatePolicy.decide(status)
        UpdateDiagnostics.recordCheck(status, action, flexibleFlowStarted)

        when (action) {
            InAppUpdateAction.IMMEDIATE -> {
                if (!startFlow(info, AppUpdateType.IMMEDIATE)) {
                    maybeShowPlayStoreFallback(status, "immediate_start_failed")
                }
            }
            InAppUpdateAction.FLEXIBLE -> offerFlexibleUpdate(info, status)
            InAppUpdateAction.NONE -> {
                if (status.updateAvailable) {
                    maybeShowPlayStoreFallback(status, "no_allowed_flow")
                }
            }
        }
    }

    private suspend fun offerFlexibleUpdate(info: AppUpdateInfo, status: InAppUpdateStatus) {
        if (flexibleFlowStarted) return
        val lastDismissedAt = promptStore.lastFlexibleDismissedAt()
        if (!InAppUpdateCooldown.shouldOfferFlexiblePrompt(lastDismissedAt, System.currentTimeMillis())) {
            return
        }
        registerListener()
        if (startFlow(info, AppUpdateType.FLEXIBLE)) {
            flexibleFlowStarted = true
        } else {
            maybeShowPlayStoreFallback(status, "flexible_start_failed")
        }
    }

    private suspend fun maybeShowPlayStoreFallback(status: InAppUpdateStatus, reason: String) {
        val lastShownAt = promptStore.lastPlayStoreFallbackShownAt()
        if (!InAppUpdateCooldown.shouldShowPlayStoreFallback(lastShownAt, System.currentTimeMillis())) {
            return
        }
        UpdateDiagnostics.recordPlayStoreFallback(reason, status)
        promptStore.recordPlayStoreFallbackShown()
        onPrompt(InAppUpdatePrompt.PlayStore)
    }

    private fun startFlow(info: AppUpdateInfo, @AppUpdateType type: Int): Boolean = try {
        appUpdateManager.startUpdateFlowForResult(
            info,
            updateLauncher,
            AppUpdateOptions.newBuilder(type).build(),
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to start in-app update flow", e)
        false
    }

    private fun registerListener() {
        if (!listenerRegistered) {
            appUpdateManager.registerListener(installListener)
            listenerRegistered = true
        }
    }

    private fun unregisterListener() {
        if (listenerRegistered) {
            appUpdateManager.unregisterListener(installListener)
            listenerRegistered = false
        }
    }

    private companion object {
        const val TAG = "InAppUpdate"
    }
}

private fun AppUpdateInfo.toStatus(): InAppUpdateStatus = InAppUpdateStatus(
    updateAvailable = updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
    flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
    immediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
    priority = updatePriority(),
    stalenessDays = clientVersionStalenessDays(),
    updateInProgress = updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
)
