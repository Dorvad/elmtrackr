package com.elmtrackr.wear

import android.util.Log
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 3-2-1 pre-punch countdown shown as a full-screen overlay; tap to cancel. */
data class PunchCountdown(
    val secondsLeft: Int,
    val isPunchIn: Boolean,
)

class WearMainViewModel(
    private val app: ElmTrackrWearApp,
) : ViewModel() {

    private var countdownJob: Job? = null

    // Cold ticker: it runs only while displayState has subscribers, so the
    // per-second loop stops (after the 5s grace) when the screen is off or
    // the app is backgrounded instead of draining the watch battery forever.
    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

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

    init {
        // The repository already swallows its own failures; the extra guard is
        // here because this runs on viewModelScope, and an exception escaping a
        // viewModelScope coroutine reaches the default handler and kills the
        // process while the first frame is still being composed.
        viewModelScope.launch {
            runCatchingCancellable {
                app.wearStateRepository.bootstrap()
                app.wearActionClient.requestRefreshFromPhone()
            }.onFailure { Log.w(TAG, "Watch state bootstrap failed", it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatchingCancellable { app.wearActionClient.requestRefreshFromPhone() }
                .onFailure { Log.w(TAG, "Refresh from the phone failed", it) }
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
            submitPunch(isPunchIn)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _punchCountdown.value = null
    }

    private fun submitPunch(isPunchIn: Boolean) {
        viewModelScope.launch {
            runCatchingCancellable { punch(isPunchIn) }
                .onFailure { Log.w(TAG, "Punch submission failed", it) }
        }
    }

    private suspend fun punch(isPunchIn: Boolean) {
        val result = if (isPunchIn) app.wearActionClient.punchIn() else app.wearActionClient.punchOut()
        if (result.success) {
            app.wearStateRepository.showConfirmation(
                app.getString(if (isPunchIn) R.string.confirmed_in else R.string.confirmed_out),
            )
        } else {
            app.wearStateRepository.showConfirmation(failureMessage(result.errorCode), isSuccess = false)
            // A failed punch usually means the face is stale — e.g. punching
            // out after the phone already ended the shift — so re-pull the
            // phone state instead of leaving a face that fails the same way
            // on every tap.
            app.wearActionClient.requestRefreshFromPhone()
        }
    }

    private fun failureMessage(errorCode: String?): String = when (errorCode) {
        "phone_unreachable" -> app.getString(R.string.phone_unreachable)
        // Named explicitly because this one is the user's own setting, not a
        // fault: "Punch failed" would send them looking for a problem that is a
        // toggle on the phone.
        "sync_disabled" -> app.getString(R.string.wear_sync_disabled)
        else -> app.getString(R.string.punch_failed)
    }

    fun dismissConfirmation() {
        app.wearStateRepository.dismissConfirmation()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val TAG = "WearMainViewModel"
    }
}
