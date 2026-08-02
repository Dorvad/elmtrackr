package com.elmtrackr.app.fake

import com.elmtrackr.app.review.ReviewPromptRecorder

class FakeReviewPromptRecorder : ReviewPromptRecorder {
    var foregrounds = 0
    var clockIns = 0
    var shiftsCompleted = 0
    var monthlyReportsViewed = 0
    var reportsExported = 0
    var discouragingEvents = 0

    override fun noteAppForegrounded() { foregrounds++ }
    override fun noteClockIn() { clockIns++ }
    override fun noteShiftCompleted() { shiftsCompleted++ }
    override fun noteMonthlyReportViewed() { monthlyReportsViewed++ }
    override fun noteReportExported() { reportsExported++ }
    override fun noteDiscouragingEvent() { discouragingEvents++ }
}
