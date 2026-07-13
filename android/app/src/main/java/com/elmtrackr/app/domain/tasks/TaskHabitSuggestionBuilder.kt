package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class TaskSuggestion(
    val task: Task,
    val isHabitBased: Boolean,
    val showSuggestedNow: Boolean = false,
    val explanation: String? = null,
)

object TaskHabitSuggestionBuilder {

    private const val SAME_DAY_OF_WEEK_SCORE = 3
    private const val SAME_HOUR_BUCKET_SCORE = 3
    private const val RECENT_USAGE_SCORE = 2
    private const val USAGE_COUNT_CAP = 5
    private const val RECENT_DAYS = 14
    private const val HOUR_BUCKET_SIZE = 2
    private const val MIN_HISTORY = 3

    fun suggest(
        tasks: List<Task>,
        completedShifts: List<Shift>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TaskSuggestion? {
        val active = tasks.filter { !it.isArchived }
        if (active.isEmpty()) return null

        val defaultRules = TaskDefaultRulesBuilder.buildRules(active, completedShifts, zoneId)
        val matchingRule = TaskDefaultRulesBuilder.ruleForInstant(defaultRules, now, zoneId)
        matchingRule?.let { rule ->
            active.firstOrNull { it.id == rule.taskId }?.let { task ->
                val slotLabel = TaskDefaultRulesBuilder.formatSlot(now, zoneId)
                return TaskSuggestion(
                    task = task,
                    isHabitBased = true,
                    showSuggestedNow = true,
                    explanation = "You usually pick ${task.name} on $slotLabel",
                )
            }
        }

        val activeIds = active.map { it.id }.toSet()
        val shiftsWithTasks = completedShifts.filter { shift ->
            shift.taskId != null && shift.taskId in activeIds
        }

        if (shiftsWithTasks.size < MIN_HISTORY) {
            val fallback = active.maxByOrNull { it.lastUsedAt ?: it.createdAt }
                ?: active.maxByOrNull { it.createdAt }
            return fallback?.let {
                TaskSuggestion(
                    task = it,
                    isHabitBased = false,
                    showSuggestedNow = false,
                    explanation = if (it.lastUsedAt != null) {
                        "Last used ${it.name}"
                    } else {
                        null
                    },
                )
            }
        }

        val zonedNow = now.atZone(zoneId)
        val currentDow = zonedNow.dayOfWeek.value % 7
        val currentHourBucket = zonedNow.hour / HOUR_BUCKET_SIZE

        val scores = mutableMapOf<String, Int>()
        val lastUsed = mutableMapOf<String, Instant>()

        for (shift in shiftsWithTasks) {
            val taskId = shift.taskId ?: continue
            var score = scores.getOrDefault(taskId, 0)
            val shiftZoned = shift.startTime.atZone(zoneId)
            if (shiftZoned.dayOfWeek.value % 7 == currentDow) score += SAME_DAY_OF_WEEK_SCORE
            if (shiftZoned.hour / HOUR_BUCKET_SIZE == currentHourBucket) score += SAME_HOUR_BUCKET_SCORE
            if (ChronoUnit.DAYS.between(shift.startTime, now) in 0..RECENT_DAYS) score += RECENT_USAGE_SCORE
            scores[taskId] = score
            val prev = lastUsed[taskId]
            if (prev == null || shift.startTime.isAfter(prev)) lastUsed[taskId] = shift.startTime
        }

        for ((taskId, count) in shiftsWithTasks.mapNotNull { it.taskId }.groupingBy { it }.eachCount()) {
            scores[taskId] = scores.getOrDefault(taskId, 0) + minOf(count, USAGE_COUNT_CAP)
        }

        val bestEntry = scores.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { lastUsed[it.key] ?: Instant.EPOCH },
        )

        val suggested = bestEntry?.key?.let { id -> active.firstOrNull { it.id == id } }
            ?: active.maxByOrNull { it.lastUsedAt ?: it.createdAt }
            ?: active.maxByOrNull { it.createdAt }

        return suggested?.let { task ->
            val habitBased = bestEntry != null && bestEntry.value > 0
            val slotLabel = TaskDefaultRulesBuilder.formatSlot(now, zoneId)
            TaskSuggestion(
                task = task,
                isHabitBased = habitBased,
                showSuggestedNow = habitBased,
                explanation = if (habitBased) {
                    "Based on your recent shifts around $slotLabel"
                } else {
                    "Last used ${task.name}"
                },
            )
        }
    }
}
