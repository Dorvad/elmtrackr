package com.elmtrackr.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.notification.NotificationPermissionCoordinator
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.ui.security.AppLockGate
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.update.InAppUpdateHost
import com.elmtrackr.app.update.InAppUpdateManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onNotificationPermissionResult?.invoke(granted)
        onNotificationPermissionResult = null
    }

    var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private var flexibleUpdateReady by mutableStateOf(false)
    private lateinit var inAppUpdateManager: InAppUpdateManager
    private var unlockNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inAppUpdateManager = InAppUpdateManager(this) { flexibleUpdateReady = true }
        intent?.data?.toString()?.let { handleDeepLink(it) }
        setContent {
            val app = application as ElmTrackrApp
            val configuration = LocalConfiguration.current
            val preferences by app.appPreferences.preferences
                .collectAsState(initial = AppPreferenceValues())
            val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val darkTheme = when (preferences.selectedTheme) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            @Suppress("UNUSED_VARIABLE")
            val refreshLock = unlockNonce
            ElmTrackrTheme(darkTheme = darkTheme) {
                AppLockGate(
                    activity = this,
                    lockEnabled = preferences.appLockEnabled,
                    onUnlocked = { unlockNonce++ },
                ) {
                    InAppUpdateHost(
                        updateReady = flexibleUpdateReady,
                        onInstall = {
                            flexibleUpdateReady = false
                            inAppUpdateManager.completeFlexibleUpdate()
                        },
                        onDismiss = { flexibleUpdateReady = false },
                    ) {
                        AppNavGraph()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AppLockController.lock()
        }
    }

    override fun onResume() {
        super.onResume()
        (application as ElmTrackrApp).refreshDynamicShortcuts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(uriString: String) {
        lifecycleScope.launch {
            (application as ElmTrackrApp).authRepository.handleDeepLink(uriString)
        }
    }

    fun requestNotificationPermission(onResult: (Boolean) -> Unit = {}) {
        if (NotificationPermissionCoordinator.hasPermission(this)) {
            onResult(true)
            return
        }
        onNotificationPermissionResult = onResult
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
