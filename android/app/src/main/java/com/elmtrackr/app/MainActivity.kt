package com.elmtrackr.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.notification.NotificationPermissionCoordinator
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.ui.security.AppLockGate
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.update.InAppUpdateHost
import com.elmtrackr.app.update.InAppUpdateManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// AppCompatActivity (a FragmentActivity subclass, so BiometricPrompt keeps
// working) is required for AppCompatDelegate.setApplicationLocales to apply
// and persist the in-app language on Android 12 and below.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: Lazy<AuthRepository>
    @Inject lateinit var appPreferences: AppPreferencesRepository

    val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onNotificationPermissionResult?.invoke(granted)
        onNotificationPermissionResult = null
    }

    var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private var flexibleUpdateReady by mutableStateOf(false)
    private lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inAppUpdateManager = InAppUpdateManager(this) { flexibleUpdateReady = true }
        intent?.data?.toString()?.let { handleDeepLink(it) }
        setContent {
            val configuration = LocalConfiguration.current
            val preferences by appPreferences.preferences
                .collectAsState(initial = AppPreferenceValues())
            val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val darkTheme = when (preferences.selectedTheme) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            ElmTrackrTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalReduceMotion provides preferences.reduceMotionEnabled) {
                    AppLockGate(
                        activity = this,
                        lockEnabled = preferences.appLockEnabled,
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
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AppLockController.lock()
        }
    }

    override fun onResume() {
        super.onResume()
        AppEntryPoints.background(this).dynamicShortcutsRefresher().refresh()
        requestNotificationPermissionForActiveShift()
    }

    /**
     * Users who clock in from the widget, shortcut, or watch never pass the
     * in-app clock-in button — the only place the POST_NOTIFICATIONS request
     * lived — so on Android 13+ their ongoing notification and every reminder
     * stayed silently disabled. If a shift is running and the permission is
     * still missing when the app opens, ask now (once per install, mirroring
     * the educational-prompt bookkeeping).
     */
    private fun requestNotificationPermissionForActiveShift() {
        if (NotificationPermissionCoordinator.hasPermission(this)) return
        lifecycleScope.launch {
            val deps = AppEntryPoints.background(this@MainActivity)
            val userId = deps.currentUserProvider().currentUserId() ?: return@launch
            val active = deps.shiftsRepository().observeActiveShift(userId).firstOrNull() != null
            if (!active) return@launch
            if (!NotificationPermissionCoordinator.shouldShowEducationalPrompt(this@MainActivity)) return@launch
            NotificationPermissionCoordinator.markPromptShown(this@MainActivity)
            requestNotificationPermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(uriString: String) {
        lifecycleScope.launch {
            authRepository.get().handleDeepLink(uriString)
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
