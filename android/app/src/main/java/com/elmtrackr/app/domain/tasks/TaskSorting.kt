package com.elmtrackr.app.domain.tasks

import com.elmtrackr.app.domain.model.Task
import java.time.Instant

object TaskSorting {

    /** Most recently used first; never-used tasks fall back to creation date. */
    fun byRecency(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareByDescending<Task> { it.lastUsedAt ?: Instant.EPOCH }
                .thenByDescending { it.createdAt },
        )
}
