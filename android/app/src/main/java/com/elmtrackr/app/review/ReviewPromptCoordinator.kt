package com.elmtrackr.app.review

import com.elmtrackr.app.data.local.preferences.AppPreferencesStore
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue between feature code, [ReviewPromptPolicy] and the Play review UI.
 *
 * Features report signals through [ReviewPromptRecorder]; positive milestones
 * (shift completed, monthly report viewed, report exported) also re-evaluate
 * eligibility, and when every rule passes a request is emitted on
 * [reviewRequests] for the foreground activity to hand to Google Play.
 *
 * The attempt is recorded *before* Play is asked: Play never says whether the
 * review UI was shown, so the cooldown has to key off our attempt, not off an
 * unobservable outcome.
 */
@Singleton
class ReviewPromptCoordinator @Inject constructor(
    private val store: ReviewPromptStore,
    private val shiftsRepository: ShiftsRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val appPreferences: AppPreferencesStore,
    @ApplicationScope private val scope: CoroutineScope,
) : ReviewPromptRecorder {

    // No replay: a request that fires while no activity is collecting is simply
    // dropped — the next milestone re-evaluates, and a stale prompt popping up
    // minutes after the moment that earned it would feel wrong anyway.
    private val _reviewRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewRequests: SharedFlow<Unit> = _reviewRequests

    override fun noteAppForegrounded() {
        scope.launch {
            val now = System.currentTimeMillis()
            store.recordUsage(now, LocalDate.now(ZoneId.systemDefault()).toEpochDay())
        }
    }

    override fun noteClockIn() {
        scope.launch { store.recordClockIn(System.currentTimeMillis()) }
    }

    override fun noteShiftCompleted() {
        scope.launch { maybeRequestReview() }
    }

    override fun noteMonthlyReportViewed() {
        scope.launch {
            store.recordMonthlyReportViewed(System.currentTimeMillis())
            maybeRequestReview()
        }
    }

    override fun noteReportExported() {
        scope.launch {
            store.recordReportExported(System.currentTimeMillis())
            maybeRequestReview()
        }
    }

    override fun noteDiscouragingEvent() {
        scope.launch { store.recordDiscouragingEvent(System.currentTimeMillis()) }
    }

    private suspend fun maybeRequestReview() {
        val inputs = buildInputs()
        val verdict = ReviewPromptPolicy.evaluate(inputs)
        ReviewDiagnostics.recordEvaluation(inputs, verdict)
        if (!verdict.eligible) return
        store.recordAttempt(inputs.nowMs)
        _reviewRequests.tryEmit(Unit)
    }

    private suspend fun buildInputs(): ReviewPromptInputs {
        val stored = store.snapshot()
        val userId = currentUserProvider.currentUserId()
        val recentShifts = userId?.let {
            shiftsRepository.observeRecentCompletedShifts(it, RECENT_SHIFT_WINDOW).first()
        }.orEmpty()
        val shiftActive = userId?.let {
            shiftsRepository.observeActiveShift(it).first()
        } != null
        val onboardingActive = !appPreferences.currentPreferences().onboardingCompleted
        return ReviewPromptInputs(
            nowMs = System.currentTimeMillis(),
            validShiftCount = ReviewPromptPolicy.countValidShifts(recentShifts),
            monthlyReportViewedAt = stored.monthlyReportViewedAt,
            reportExportedAt = stored.reportExportedAt,
            usageDayCount = stored.usageDayCount,
            firstUsedAt = stored.firstUsedAt,
            lastClockInAt = stored.lastClockInAt,
            lastDiscouragingEventAt = stored.lastDiscouragingEventAt,
            lastAttemptAt = stored.lastAttemptAt,
            onboardingActive = onboardingActive,
            shiftActive = shiftActive,
        )
    }

    private companion object {
        /** Plenty to establish the 5-shift milestone without loading a whole history. */
        const val RECENT_SHIFT_WINDOW = 60
    }
}
