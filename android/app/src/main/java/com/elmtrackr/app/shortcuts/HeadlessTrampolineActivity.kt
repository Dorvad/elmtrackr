package com.elmtrackr.app.shortcuts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Transparent trampoline for the dynamic Clock Out app shortcut.
 * Performs clock-out without opening the main UI.
 */
class HeadlessTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                val result = ClockOutActions.clockOutActiveShift(applicationContext)
                ClockOutActions.showShortcutFeedback(applicationContext, result)
            } finally {
                finish()
            }
        }
    }
}
