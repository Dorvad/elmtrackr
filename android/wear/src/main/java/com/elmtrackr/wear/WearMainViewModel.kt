package com.elmtrackr.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.wear.sync.WearDisplayMath
import com.elmtrackr.wear.sync.WearDisplayState
import com.elmtrackr.wear.sync.WearShiftSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class WearMainViewModel(
    private val app: ElmTrackrWearApp,
) : ViewModel() {

    private val tick = MutableStateFlow(System.currentTimeMillis())
    private var tickerJob: Job? = null

    val displayState: StateFlow<WearDisplayState> = combine(
        app.wearStateRepository.snapshot,
        tick,
    ) { snapshot, now ->
        WearDisplayMath.displayFor(snapshot, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearDisplayMath.displayFor(WearShiftSnapshot.signedOut()))

    val confirmationMessage: StateFlow<String?> = app.wearStateRepository.confirmationMessage
    val isPunchInProgress: StateFlow<Boolean> = app.wearStateRepository.isPunchInProgress

    val systemTimeLabel: StateFlow<String> = tick
        .combine(app.wearStateRepository.snapshot) { _, _ ->
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            app.wearStateRepository.bootstrap()
            app.wearActionClient.requestRefreshFromPhone()
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                tick.value = System.currentTimeMillis()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            app.wearActionClient.requestRefreshFromPhone()
        }
    }

    fun punchIn() {
        viewModelScope.launch {
            val result = app.wearActionClient.punchIn()
            if (result.success) {
                app.wearStateRepository.showConfirmation("Clocked in")
            }
        }
    }

    fun punchOut() {
        viewModelScope.launch {
            val result = app.wearActionClient.punchOut()
            if (result.success) {
                app.wearStateRepository.showConfirmation("Clocked out")
            }
        }
    }
}
