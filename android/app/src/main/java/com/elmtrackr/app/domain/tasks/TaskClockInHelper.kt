package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import kotlinx.coroutines.flow.first

data class TaskClockInParams(
    val taskId: String?,
    val taskNameSnapshot: String?,
    val taskIconSnapshot: String?,
    val taskHourlyRateSnapshot: Double?,
)

object TaskClockInHelper {

    fun paramsFromTask(task: Task?): TaskClockInParams = TaskClockInParams(
        taskId = task?.id,
        taskNameSnapshot = task?.name,
        taskIconSnapshot = task?.icon,
        taskHourlyRateSnapshot = task?.hourlyRate,
    )

    suspend fun resolveAutoTask(
        tasksRepository: TasksRepository,
        shiftsRepository: ShiftsRepository,
        userId: String,
    ): Task? {
        val tasks = tasksRepository.getActiveTasks(userId)
        if (tasks.isEmpty()) return null
        val recent = shiftsRepository.observeRecentCompletedShifts(userId, 60).first()
        return TaskHabitSuggestionBuilder.suggest(tasks, recent)?.task
            ?: tasks.maxByOrNull { it.createdAt }
    }
}
