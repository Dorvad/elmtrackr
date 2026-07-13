package com.elmtrackr.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.wear.sync.WearConfirmation
import com.elmtrackr.wear.sync.WearDisplayMath
import com.elmtrackr.wear.sync.WearDisplayState
import com.elmtrackr.wear.sync.WearShiftSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 3-2-1 pre-punch countdown shown as a full-screen overlay; tap to cancel. */
data class PunchCountdown(
    val secondsLeft: Int,
    val isPunchIn: Boolean,
)

class WearMainViewModel(
    private val app: ElmTrackrWearApp,
) : ViewModel() {

    private val tick = MutableStateFlow(System.currentTimeMillis())
    private var tickerJob: Job? = null
    private var countdownJob: Job? = null

    val displayState: StateFlow<WearDisplayState> = combine(
        app.wearStateRepository.snapshot,
        tick,
    ) { snapshot, now ->
        WearDisplayMath.displayFor(snapshot, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearDisplayMath.displayFor(WearShiftSnapshot.signedOut()))

    val confirmation: StateFlow<WearConfirmation?> = app.wearStateRepository.confirmation
    val isPunchInProgress: StateFlow<Boolean> = app.wearStateRepository.isPunchInProgress

    private val _punchCountdown = MutableStateFlow<PunchCountdown?>(null)
    val punchCountdown: StateFlow<PunchCountdown?> = _punchCountdown.asStateFlow()

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

    fun requestPunchIn() = startCountdown(isPunchIn = true)

    fun requestPunchOut() = startCountdown(isPunchIn = false)

    private fun startCountdown(isPunchIn: Boolean) {
        if (countdownJob?.isActive == true || isPunchInProgress.value) return
        countdownJob = viewModelScope.launch {
            for (second in COUNTDOWN_SECONDS downTo 1) {
                _punchCountdown.value = PunchCountdown(second, isPunchIn)
                delay(1_000)
            }
            _punchCountdown.value = null
            if (isPunchIn) punchIn() else punchOut()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _punchCountdown.value = null
    }

    fun punchIn() {
        viewModelScope.launch {
            val result = app.wearActionClient.punchIn()
            if (result.success) {
                app.wearStateRepository.showConfirmation(app.getString(R.string.confirmed_in))
            } else {
                app.wearStateRepository.showConfirmation(failureMessage(result.errorCode), isSuccess = false)
            }
        }
    }

    fun punchOut() {
        viewModelScope.launch {
            val result = app.wearActionClient.punchOut()
            if (result.success) {
                app.wearStateRepository.showConfirmation(app.getString(R.string.confirmed_out))
            } else {
                app.wearStateRepository.showConfirmation(failureMessage(result.errorCode), isSuccess = false)
            }
        }
    }

    private fun failureMessage(errorCode: String?): String = when (errorCode) {
        "phone_unreachable" -> app.getString(R.string.phone_unreachable)
        else -> app.getString(R.string.punch_failed)
    }

    fun dismissConfirmation() {
        app.wearStateRepository.dismissConfirmation()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
    }
}
