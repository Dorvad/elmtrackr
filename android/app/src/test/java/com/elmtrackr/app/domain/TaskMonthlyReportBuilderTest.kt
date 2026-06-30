package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TaskMonthlyReportBuilderTest {

    private val settings = UserSettings(
        id = "cfg",
        userId = "u1",
        hourlyRate = 100.0,
    )

    private fun task(id: String, name: String) = Task(
        id = id,
        userId = "u1",
        name = name,
        icon = "💼",
        hourlyRate = 100.0,
    )

    private fun shift(id: String, taskId: String, start: String, end: String) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        taskId = taskId,
        taskNameSnapshot = "Task",
        taskIconSnapshot = "💼",
        taskHourlyRateSnapshot = 100.0,
    )

    @Test
    fun `groups completed shifts by task`() {
        val tasks = listOf(task("a", "Alpha"), task("b", "Beta"))
        val shifts = listOf(
            shift("s1", "a", "2024-01-08T09:00:00Z", "2024-01-08T13:00:00Z"),
            shift("s2", "a", "2024-01-09T09:00:00Z", "2024-01-09T11:00:00Z"),
            shift("s3", "b", "2024-01-10T09:00:00Z", "2024-01-10T17:00:00Z"),
        )
        val breakdown = TaskMonthlyReportBuilder.build(shifts, settings, tasks)
        assertEquals(2, breakdown.size)
        val alpha = breakdown.first { it.taskId == "a" }
        assertEquals(2, alpha.shiftCount)
        assertEquals(360, alpha.totalMinutes)
        assertEquals(180, alpha.averageShiftMinutes)
        assertTrue((alpha.totalPay ?: 0.0) > 0)
    }
}
