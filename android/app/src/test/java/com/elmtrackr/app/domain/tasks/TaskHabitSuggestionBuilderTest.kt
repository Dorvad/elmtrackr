package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskHabitSuggestionBuilderTest {

    private val userId = "user-1"
    private val zone = ZoneId.of("UTC")

    private fun task(id: String, name: String, createdAt: Instant) = Task(
        id = id,
        userId = userId,
        name = name,
        icon = "💼",
        hourlyRate = 50.0,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun shift(taskId: String, start: Instant) = Shift(
        id = "shift-$taskId-$start",
        userId = userId,
        startTime = start,
        endTime = start.plusSeconds(3600),
        taskId = taskId,
        taskNameSnapshot = "Task",
        taskIconSnapshot = "💼",
        taskHourlyRateSnapshot = 50.0,
    )

    @Test
    fun `returns most recently created task when history is insufficient`() {
        val tasks = listOf(
            task("a", "Older", Instant.parse("2024-01-01T00:00:00Z")),
            task("b", "Newer", Instant.parse("2024-02-01T00:00:00Z")),
        )
        val suggestion = TaskHabitSuggestionBuilder.suggest(tasks, emptyList())
        assertEquals("b", suggestion?.task?.id)
        assertFalse(suggestion?.isHabitBased == true)
    }

    @Test
    fun `prefers task used on same weekday and hour bucket`() {
        val tasks = listOf(
            task("a", "A", Instant.parse("2024-01-01T00:00:00Z")),
            task("b", "B", Instant.parse("2024-01-02T00:00:00Z")),
        )
        val now = Instant.parse("2024-03-04T10:00:00Z") // Monday ~10:00
        val history = listOf(
            shift("a", Instant.parse("2024-02-26T10:00:00Z")),
            shift("a", Instant.parse("2024-02-19T10:00:00Z")),
            shift("b", Instant.parse("2024-02-20T14:00:00Z")),
        )
        val suggestion = TaskHabitSuggestionBuilder.suggest(tasks, history, now = now, zoneId = zone)
        assertEquals("a", suggestion?.task?.id)
    }
}
