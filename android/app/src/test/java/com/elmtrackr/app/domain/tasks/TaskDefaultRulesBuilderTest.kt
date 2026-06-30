package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskDefaultRulesBuilderTest {

    private val zone = ZoneId.of("UTC")

    private fun task(id: String, name: String) = Task(
        id = id,
        userId = "u1",
        name = name,
        icon = "💼",
        hourlyRate = 50.0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun shift(taskId: String, start: Instant) = Shift(
        id = "shift-$taskId-$start",
        userId = "u1",
        startTime = start,
        endTime = start.plusSeconds(3600),
        taskId = taskId,
    )

    @Test
    fun `returns empty rules until enough history`() {
        val tasks = listOf(task("a", "Alpha"))
        val rules = TaskDefaultRulesBuilder.buildRules(tasks, emptyList(), zone)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `learns day and time slot after enough repeats`() {
        val tasks = listOf(task("a", "Alpha"), task("b", "Beta"))
        val history = List(16) {
            shift("a", Instant.parse("2024-01-08T10:00:00Z"))
        }
        val rules = TaskDefaultRulesBuilder.buildRules(tasks, history, zone)
        assertTrue(rules.isNotEmpty())
        val mondayMorning = rules.firstOrNull { it.dayOfWeek == 1 && it.hourBucket == 5 }
        assertEquals("a", mondayMorning?.taskId)
    }
}
