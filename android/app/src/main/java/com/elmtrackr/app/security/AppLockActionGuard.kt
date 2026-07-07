package com.elmtrackr.app.security

import android.widget.Toast
import com.elmtrackr.app.R
import com.elmtrackr.app.language.withAppLocale

object AppLockActionGuard {

    fun blockIfLocked(context: android.content.Context): Boolean {
        if (!AppLockController.shouldBlockSensitiveActions()) return false
        Toast.makeText(
            context.applicationContext,
            context.withAppLocale().getString(R.string.app_lock_action_blocked),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }
}
