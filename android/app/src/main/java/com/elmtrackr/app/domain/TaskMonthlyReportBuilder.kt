package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.TaskMonthlyBreakdown
import com.elmtrackr.app.domain.model.UserSettings

object TaskMonthlyReportBuilder {

    fun build(
        shifts: List<Shift>,
        settings: UserSettings,
        tasks: List<Task> = emptyList(),
        profiles: List<CompensationProfile> = emptyList(),
    ): List<TaskMonthlyBreakdown> {
        val completed = shifts.filter { it.isCompleted }
        if (completed.isEmpty()) return emptyList()

        val taskById = tasks.associateBy { it.id }
        val grouped = completed.groupBy { shift ->
            shift.taskId?.let { "id:$it" }
                ?: shift.taskNameSnapshot?.let { "name:${it.trim().lowercase()}" }
                ?: "none"
        }

        return grouped.mapNotNull { (key, groupShifts) ->
            if (key == "none") return@mapNotNull null
            val first = groupShifts.first()
            val taskId = first.taskId
            val liveTask = taskId?.let { taskById[it] }
            val name = liveTask?.name ?: first.taskNameSnapshot ?: "Unknown task"
            val icon = liveTask?.icon ?: first.taskIconSnapshot
            val color = liveTask?.color

            var totalMinutes = 0
            var regularMinutes = 0
            var overtimeMinutes = 0
            var weekendMinutes = 0
            var totalPay = 0.0
            var payKnown = false

            for (shift in groupShifts) {
                val breakdown = MonthlyReportBuilder.buildShiftBreakdown(shift, settings)
                totalMinutes += breakdown.totalMinutes
                regularMinutes += breakdown.regularMinutes
                overtimeMinutes += breakdown.overtimeMinutes
                weekendMinutes += breakdown.weekendMinutes
                PayrollCalculator.calculateShiftPayInContext(shift, completed, settings, profiles)?.let { pay ->
                    totalPay += pay.totalGross
                    payKnown = true
                }
            }

            TaskMonthlyBreakdown(
                taskKey = key,
                taskId = taskId,
                name = name,
                icon = icon,
                color = color,
                shiftCount = groupShifts.size,
                totalMinutes = totalMinutes,
                regularMinutes = regularMinutes,
                overtimeMinutes = overtimeMinutes,
                weekendMinutes = weekendMinutes,
                totalPay = if (payKnown) totalPay else null,
                averageShiftMinutes = totalMinutes / groupShifts.size,
            )
        }.sortedWith(
            compareByDescending<TaskMonthlyBreakdown> { it.totalMinutes }
                .thenBy { it.name.lowercase() },
        )
    }
}
