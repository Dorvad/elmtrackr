package com.elmtrackr.app.update

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Drives Google Play in-app updates for [activity].
 *
 * On every resume it asks Play whether an update is available and applies
 * [InAppUpdatePolicy] to start the flexible or immediate flow. For flexible
 * updates it watches the install state and invokes [onFlexibleUpdateReady] once
 * the new version finishes downloading, so the UI can prompt the user to restart
 * via [completeFlexibleUpdate].
 *
 * Outside of Google Play (sideloaded debug builds, CI, emulators without Play)
 * the Play task fails or reports "no update available" and every method becomes a
 * safe no-op — the app behaves exactly as it did before.
 */
class InAppUpdateManager(
    private val activity: ComponentActivity,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
    private val onFlexibleUpdateReady: () -> Unit,
) : DefaultLifecycleObserver {

    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            // RESULT_CANCELED (user declined) or RESULT_IN_APP_UPDATE_FAILED. We do not
            // re-prompt for a flexible update again this session to avoid nagging; an
            // immediate update is re-offered on the next resume by the policy.
            Log.d(TAG, "Update flow did not complete (resultCode=${result.resultCode}).")
        }
    }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onFlexibleUpdateReady()
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
                // A flexible update finished downloading while we were away — prompt to install.
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    onFlexibleUpdateReady()
                    return@addOnSuccessListener
                }
                when (InAppUpdatePolicy.decide(info.toStatus())) {
                    InAppUpdateAction.IMMEDIATE -> startFlow(info, AppUpdateType.IMMEDIATE)
                    InAppUpdateAction.FLEXIBLE -> if (!flexibleFlowStarted) {
                        registerListener()
                        if (startFlow(info, AppUpdateType.FLEXIBLE)) {
                            flexibleFlowStarted = true
                        }
                    }
                    InAppUpdateAction.NONE -> Unit
                }
            }
            .addOnFailureListener { e ->
                // Expected when the app was not installed from Google Play.
                Log.d(TAG, "Skipping in-app update check: ${e.message}")
            }
    }

    /** Completes a downloaded flexible update by restarting the app to install it. */
    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
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
