package com.elmtrackr.wear.tile

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearPunchTrampolineActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ElmTrackrWearApp
        val action = intent.getStringExtra(EXTRA_ACTION)
        scope.launch {
            val result = when (action) {
                ACTION_IN -> app.wearActionClient.punchIn()
                ACTION_OUT -> app.wearActionClient.punchOut()
                else -> return@launch finish()
            }
            if (result.success) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                val message = if (action == ACTION_IN) {
                    getString(R.string.confirmed_in)
                } else {
                    getString(R.string.confirmed_out)
                }
                app.wearStateRepository.showConfirmation(message)
            } else {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_IN = "punch_in"
        const val ACTION_OUT = "punch_out"
    }
}
