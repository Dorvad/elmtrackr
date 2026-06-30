package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class TaskDefaultRule(
    val dayOfWeek: Int,
    val hourBucket: Int,
    val taskId: String,
    val taskName: String,
    val occurrenceCount: Int,
)

object TaskDefaultRulesBuilder {

    private const val MIN_TOTAL_SHIFTS = 15
    private const val MIN_SLOT_OCCURRENCES = 3
    private const val HOUR_BUCKET_SIZE = 2

    fun buildRules(
        tasks: List<Task>,
        completedShifts: List<Shift>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TaskDefaultRule> {
        val activeIds = tasks.filter { !it.isArchived }.map { it.id }.toSet()
        val nameById = tasks.associate { it.id to it.name }
        val shiftsWithTasks = completedShifts.filter { shift ->
            shift.taskId != null && shift.taskId in activeIds
        }
        if (shiftsWithTasks.size < MIN_TOTAL_SHIFTS) return emptyList()

        val slotCounts = mutableMapOf<Pair<Int, Int>, MutableMap<String, Int>>()
        for (shift in shiftsWithTasks) {
            val taskId = shift.taskId ?: continue
            val zoned = shift.startTime.atZone(zoneId)
            val slot = (zoned.dayOfWeek.value % 7) to (zoned.hour / HOUR_BUCKET_SIZE)
            slotCounts.getOrPut(slot) { mutableMapOf() }
                .merge(taskId, 1, Int::plus)
        }

        return slotCounts.mapNotNull { (slot, counts) ->
            val best = counts.maxByOrNull { it.value } ?: return@mapNotNull null
            if (best.value < MIN_SLOT_OCCURRENCES) return@mapNotNull null
            val name = nameById[best.key] ?: return@mapNotNull null
            TaskDefaultRule(
                dayOfWeek = slot.first,
                hourBucket = slot.second,
                taskId = best.key,
                taskName = name,
                occurrenceCount = best.value,
            )
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.hourBucket }))
    }

    fun ruleForInstant(
        rules: List<TaskDefaultRule>,
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TaskDefaultRule? {
        val zoned = instant.atZone(zoneId)
        val slot = (zoned.dayOfWeek.value % 7) to (zoned.hour / HOUR_BUCKET_SIZE)
        return rules.firstOrNull { it.dayOfWeek == slot.first && it.hourBucket == slot.second }
    }

    fun formatRule(rule: TaskDefaultRule, locale: Locale = Locale.getDefault()): String {
        val day = DayOfWeek.of(if (rule.dayOfWeek == 0) 7 else rule.dayOfWeek)
            .getDisplayName(TextStyle.SHORT, locale)
        val startHour = rule.hourBucket * HOUR_BUCKET_SIZE
        val endHour = (startHour + HOUR_BUCKET_SIZE).coerceAtMost(24)
        return "$day ${formatHour(startHour)}–${formatHour(endHour)} → ${rule.taskName}"
    }

    fun formatSlot(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val zoned = instant.atZone(zoneId)
        val day = zoned.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val bucket = zoned.hour / HOUR_BUCKET_SIZE
        val startHour = bucket * HOUR_BUCKET_SIZE
        val endHour = (startHour + HOUR_BUCKET_SIZE).coerceAtMost(24)
        return "$day ${formatHour(startHour)}–${formatHour(endHour)}"
    }

    private fun formatHour(hour: Int): String =
        when {
            hour == 0 || hour == 24 -> "12am"
            hour == 12 -> "12pm"
            hour < 12 -> "${hour}am"
            else -> "${hour - 12}pm"
        }
}
