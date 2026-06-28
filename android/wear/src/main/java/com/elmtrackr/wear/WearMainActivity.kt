package com.elmtrackr.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.elmtrackr.wear.ui.IdleScreen
import com.elmtrackr.wear.ui.RunningScreen
import com.elmtrackr.wear.ui.SetupScreen
import com.elmtrackr.wear.ui.WearAuroraTheme

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
                val confirmation by viewModel.confirmationMessage.collectAsState()
                val isLoading by viewModel.isPunchInProgress.collectAsState()
                val systemTime by viewModel.systemTimeLabel.collectAsState()
                val snapshot = display.snapshot

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        confirmation != null -> ConfirmationOverlay(confirmation!!)
                        !snapshot.signedIn -> SetupScreen(onRefresh = viewModel::refresh)
                        snapshot.isActive -> RunningScreen(
                            elapsed = display.elapsedHms,
                            sinceLabel = snapshot.startTimeLabel,
                            progressPercent = display.progressPercent,
                            onPunchOut = viewModel::punchOut,
                            isLoading = isLoading,
                        )
                        else -> IdleScreen(
                            systemTime = systemTime,
                            lastPunchLabel = snapshot.lastPunchLabel.ifBlank { snapshot.startTimeLabel },
                            todayShort = display.todayShort,
                            onPunchIn = viewModel::punchIn,
                            isLoading = isLoading,
                        )
                    }
                }
            }
        }
    }
}
