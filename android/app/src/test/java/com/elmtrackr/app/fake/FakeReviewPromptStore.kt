package com.elmtrackr.app.fake

import com.elmtrackr.app.review.ReviewPromptStore
import com.elmtrackr.app.review.StoredReviewPromptState

class FakeReviewPromptStore(
    var state: StoredReviewPromptState = StoredReviewPromptState(),
) : ReviewPromptStore {

    override suspend fun snapshot(): StoredReviewPromptState = state

    override suspend fun recordUsage(nowMs: Long, epochDay: Long) {
        val first = state.firstUsedAt ?: nowMs
        if (state.lastUsageEpochDay != epochDay) {
            state = state.copy(
                firstUsedAt = first,
                lastUsageEpochDay = epochDay,
                usageDayCount = state.usageDayCount + 1,
            )
        } else {
            state = state.copy(firstUsedAt = first)
        }
    }

    override suspend fun recordClockIn(nowMs: Long) {
        state = state.copy(lastClockInAt = nowMs)
    }

    override suspend fun recordMonthlyReportViewed(nowMs: Long) {
        state = state.copy(monthlyReportViewedAt = nowMs)
    }

    override suspend fun recordReportExported(nowMs: Long) {
        state = state.copy(reportExportedAt = nowMs)
    }

    override suspend fun recordDiscouragingEvent(nowMs: Long) {
        state = state.copy(lastDiscouragingEventAt = nowMs)
    }

    override suspend fun recordAttempt(nowMs: Long) {
        state = state.copy(lastAttemptAt = nowMs)
    }
}
