package com.elmtrackr.app.security

import android.content.Context
import android.widget.Toast
import com.elmtrackr.app.R
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.language.withAppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLockActionGuard {

    /**
     * Gate for headless punch surfaces (widget, notification action, shortcut, Wear).
     *
     * Suspending on purpose: a punch tap can cold-start the process, and the app-lock
     * preference lives in DataStore. Reading [AppLockController] synchronously at that
     * point saw an unresolved state and let the punch through with no authentication, so
     * this resolves the persisted value first and fails closed if it cannot be read.
     */
    suspend fun blockIfLocked(context: Context): Boolean {
        if (!resolveShouldBlock(context)) return false
        val appContext = context.applicationContext
        val message = context.withAppLocale().getString(R.string.app_lock_action_blocked)
        // Toasts must be posted from the main thread; these callers run on IO.
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private suspend fun resolveShouldBlock(context: Context): Boolean {
        if (!AppLockController.isConfigured()) {
            val resolved = runCatching {
                AppEntryPoints.background(context.applicationContext)
                    .appPreferences()
                    .currentPreferences()
                    .appLockEnabled
            }.getOrElse { return true }
            // A freshly resolved process has not authenticated yet, so an enabled
            // lock starts locked — same contract as AppStartupCoordinator.
            AppLockController.configure(enabled = resolved)
        }
        return AppLockController.shouldBlockSensitiveActions()
    }
}
