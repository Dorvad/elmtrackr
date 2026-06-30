package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TaskSortingTest {

    @Test
    fun `sorts by last used then created`() {
        val tasks = listOf(
            Task("a", "u1", "A", "💼", hourlyRate = 1.0, createdAt = Instant.parse("2024-01-01T00:00:00Z")),
            Task(
                "b", "u1", "B", "💼", hourlyRate = 1.0,
                createdAt = Instant.parse("2024-01-02T00:00:00Z"),
                lastUsedAt = Instant.parse("2024-03-01T00:00:00Z"),
            ),
            Task(
                "c", "u1", "C", "💼", hourlyRate = 1.0,
                createdAt = Instant.parse("2024-01-03T00:00:00Z"),
                lastUsedAt = Instant.parse("2024-02-01T00:00:00Z"),
            ),
        )
        val sorted = TaskSorting.byRecency(tasks).map { it.id }
        assertEquals(listOf("b", "c", "a"), sorted)
    }
}
