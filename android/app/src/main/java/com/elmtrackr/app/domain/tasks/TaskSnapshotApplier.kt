package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task

object TaskSnapshotApplier {

    fun applyToShift(shift: Shift, task: Task?): Shift = if (task == null) {
        shift.copy(
            taskId = null,
            taskNameSnapshot = null,
            taskIconSnapshot = null,
            taskHourlyRateSnapshot = null,
        )
    } else {
        shift.copy(
            taskId = task.id,
            taskNameSnapshot = task.name,
            taskIconSnapshot = task.icon,
            taskHourlyRateSnapshot = task.hourlyRate,
        )
    }
}
