package com.elmtrackr.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.elmtrackr.wear.ui.ConfirmationOverlay
import com.elmtrackr.wear.ui.CountdownOverlay
import com.elmtrackr.wear.ui.IdleScreen
import com.elmtrackr.wear.ui.RunningScreen
import com.elmtrackr.wear.ui.SetupScreen
import com.elmtrackr.wear.ui.WearAuroraBackground
import com.elmtrackr.wear.ui.WearAuroraTheme
import com.elmtrackr.wear.ui.WearLabels

class WearMainActivity : ComponentActivity() {

    private val viewModel: WearMainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WearMainViewModel(application as ElmTrackrWearApp) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAuroraTheme {
                val display by viewModel.displayState.collectAsState()
                val confirmation by viewModel.confirmation.collectAsState()
                val countdown by viewModel.punchCountdown.collectAsState()
                val isLoading by viewModel.isPunchInProgress.collectAsState()
                val snapshot = display.snapshot

                Box(modifier = Modifier.fillMaxSize()) {
                    WearAuroraBackground()
                    if (confirmation != null || countdown != null) {
                        BackHandler {
                            viewModel.cancelCountdown()
                            viewModel.dismissConfirmation()
                        }
                    }
                    when {
                        confirmation != null -> confirmation?.let { ConfirmationOverlay(it) }
                        !snapshot.signedIn -> SetupScreen(onRefresh = viewModel::refresh)
                        snapshot.isActive -> RunningScreen(
                            elapsed = display.elapsedHms,
                            progressPercent = display.progressPercent,
                            onPunchOut = viewModel::requestPunchOut,
                            isLoading = isLoading,
                        )
                        else -> IdleScreen(
                            lastPunchLabel = WearLabels.lastPunch(this@WearMainActivity, snapshot).ifBlank {
                                snapshot.startTimeLabel.takeIf { it != "--:--" }.orEmpty()
                            },
                            todayShort = display.todayShort,
                            onPunchIn = viewModel::requestPunchIn,
                            isLoading = isLoading,
                        )
                    }
                    countdown?.let { active ->
                        CountdownOverlay(
                            countdown = active,
                            onCancel = viewModel::cancelCountdown,
                        )
                    }
                }
            }
        }
    }
}
