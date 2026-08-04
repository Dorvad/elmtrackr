package com.elmtrackr.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.navigation.DeepLinkRouter
import com.elmtrackr.app.navigation.PaidProjectsNavGuard
import com.elmtrackr.app.notification.ActiveShiftRestorer
import com.elmtrackr.app.notification.NotificationPermissionCoordinator
import com.elmtrackr.app.review.PlayReviewFlowLauncher
import com.elmtrackr.app.review.ReviewPromptCoordinator
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.ui.security.AppLockGate
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.update.InAppUpdateHost
import com.elmtrackr.app.update.InAppUpdateManager
import com.elmtrackr.app.update.InAppUpdatePrompt
import com.elmtrackr.app.update.openPlayStoreListing
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
    @Inject lateinit var syncTrigger: SyncTrigger
    @Inject lateinit var deepLinkRouter: DeepLinkRouter
    @Inject lateinit var reviewPromptCoordinator: ReviewPromptCoordinator

    val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // A grant is the moment the ongoing notification becomes postable, and
        // nothing else was posting it: the callback only resumed whatever the caller
        // had parked here, so a user who clocked in from the widget and then granted
        // the permission in-app still had no notification and no Clock Out action
        // until the next observeActiveShift emission — which for an idle running
        // shift may not come for hours.
        if (granted) {
            lifecycleScope.launch { ActiveShiftRestorer.restore(this@MainActivity) }
        }
        onNotificationPermissionResult?.invoke(granted)
        onNotificationPermissionResult = null
    }

    var onNotificationPermissionResult: ((Boolean) -> Unit)? = null

    private var updatePrompt by mutableStateOf<InAppUpdatePrompt?>(null)
    private lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inAppUpdateManager = InAppUpdateManager(this) { prompt -> updatePrompt = prompt }
        // Collected only while RESUMED: the Play review UI needs a visible
        // activity, and a request earned while backgrounded is simply dropped
        // (the coordinator's next milestone re-evaluates). Play itself decides
        // whether any UI actually appears — the app carries on either way.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                reviewPromptCoordinator.reviewRequests.collect {
                    PlayReviewFlowLauncher.launch(this@MainActivity)
                }
            }
        }
        intent?.data?.toString()?.let { handleDeepLink(it) }
        setContent {
            val configuration = LocalConfiguration.current
            // Nullable initial value on purpose: AppLockGate must be able to tell
            // "app lock is off" from "the preference has not loaded yet".
            val storedPreferences by appPreferences.preferences
                .collectAsState(initial = null)
            val preferences = storedPreferences ?: AppPreferenceValues()
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
                        lockEnabled = storedPreferences?.appLockEnabled,
                    ) {
                        InAppUpdateHost(
                            prompt = updatePrompt,
                            onInstall = {
                                updatePrompt = null
                                inAppUpdateManager.completeFlexibleUpdate()
                            },
                            onDismiss = { updatePrompt = null },
                            onOpenPlayStore = {
                                updatePrompt = null
                                openPlayStoreListing(this@MainActivity)
                            },
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

    override fun onStart() {
        super.onStart()
        // Pull remote changes whenever the app returns to the foreground, so
        // edits made on another device show up without waiting for the
        // 15-minute periodic worker. No-op when Supabase isn't configured, and
        // WorkManager's KEEP policy debounces repeated foreground transitions.
        syncTrigger.schedule()
        reviewPromptCoordinator.noteAppForegrounded()
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

    /**
     * Auth callbacks are consumed imperatively by [AuthRepository] and never
     * reach the nav graph. Anything else (currently only
     * `elmtrackr://projects`) is parked in [DeepLinkRouter] until the tab
     * navigation graph exists to route it — the activity can receive an intent
     * long before the shell has composed, or while the user is still on auth
     * or onboarding.
     */
    private fun handleDeepLink(uriString: String) {
        if (PaidProjectsNavGuard.isProjectsDeepLink(uriString)) {
            deepLinkRouter.submit(uriString)
            return
        }
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
