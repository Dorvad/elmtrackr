package com.elmtrackr.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.wear.compose.material3.AppScaffold
import com.elmtrackr.wear.ui.ConfirmationOverlay
import com.elmtrackr.wear.ui.CountdownOverlay
import com.elmtrackr.wear.ui.IdleScreen
import com.elmtrackr.wear.ui.RunningScreen
import com.elmtrackr.wear.ui.WearAuroraBackground
import com.elmtrackr.wear.ui.WearAuroraTheme
import com.elmtrackr.wear.ui.WearLabels

class WearMainActivity : ComponentActivity() {

    private val viewModel: WearMainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ElmTrackrWearApp.from(this@WearMainActivity)
                    ?: error("WearMainActivity requires ElmTrackrWearApp")
                return WearMainViewModel(app) as T
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-pull phone state every time the watch face comes forward, and replay
        // any punches that happened on the wrist while the phone was away.
        if (ElmTrackrWearApp.from(this) != null) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAuroraTheme {
                // AppScaffold owns TimeText. Drawing TimeText ourselves, outside
                // that scaffold, is the Wear Compose Material 3 1.5 anti-pattern:
                // curved time on a round watch reads composition locals the
                // scaffold provides, and a reviewer tapping the launcher on a
                // round device is the path Robolectric's square canvas never
                // exercises.
                AppScaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,
                ) {
                    val app = ElmTrackrWearApp.from(this@WearMainActivity)
                    if (app == null) {
                        StandaloneIdleFace()
                    } else {
                        WearApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun WearApp(viewModel: WearMainViewModel) {
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
            snapshot.isActive -> RunningScreen(
                elapsed = display.elapsedHms,
                progressPercent = display.progressPercent,
                onPunchOut = viewModel::requestPunchOut,
                isLoading = isLoading,
            )
            else -> IdleScreen(
                lastPunchLabel = WearLabels.lastPunch(
                    LocalContext.current,
                    snapshot,
                ).ifBlank {
                    snapshot.startTimeLabel.takeIf { it != "--:--" }.orEmpty()
                },
                todayShort = display.todayShort.takeIf { snapshot.todayMinutes > 0 }.orEmpty(),
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

@Composable
private fun StandaloneIdleFace() {
    Box(modifier = Modifier.fillMaxSize()) {
        WearAuroraBackground()
        IdleScreen(
            lastPunchLabel = "",
            todayShort = "",
            onPunchIn = {},
            isLoading = false,
        )
    }
}
