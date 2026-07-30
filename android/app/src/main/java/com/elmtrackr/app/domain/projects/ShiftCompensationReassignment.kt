package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.Shift

/**
 * Moves a shift between compensation contexts.
 *
 * The invariant this exists to hold: a shift's time counts as employee wages or
 * as project time, never as both and never as neither. Every field that belongs
 * to the context being left is cleared in the same step that sets the new one, so
 * there is no intermediate state where a shift carries a project link and a wage
 * snapshot at once.
 *
 * Nothing here touches a project's billing records or payments. A billed amount
 * is a frozen snapshot of what the client was asked to pay; reassigning hours
 * changes the project's tracked time and therefore its effective rate, and must
 * leave the billed figures exactly as they were.
 */
object ShiftCompensationReassignment {

    /**
     * Returns [shift] recast as employee-paid hourly work.
     *
     * The project link is dropped, so the hours leave the project's tracked time.
     * The compensation snapshot is cleared rather than kept: the caller
     * recalculates it, and a snapshot taken while the shift was project time
     * would describe rules that never applied to it.
     */
    fun toEmployeePaid(shift: Shift): Shift =
        if (shift.isEmployeePaid && shift.projectId == null) {
            shift
        } else {
            shift.copy(
                compensationSource = CompensationSource.EMPLOYEE,
                projectId = null,
                projectNameSnapshot = null,
                compensationSnapshot = null,
            )
        }

    /**
     * Returns [shift] recast as time tracked against [project].
     *
     * The compensation snapshot is cleared because this shift no longer produces
     * wages, overtime or premiums — leaving a wage snapshot on it would preserve
     * numbers that no calculation should ever read again.
     *
     * The task link is deliberately kept: a task records *what* was worked on,
     * which stays true regardless of who pays for it. Employee pay never reads a
     * project, and [com.elmtrackr.app.domain.PayrollCalculator] refuses project
     * shifts outright, so the task's rate cannot reach a wage.
     */
    fun toProjectTime(shift: Shift, project: Project): Shift = shift.copy(
        compensationSource = CompensationSource.PROJECT,
        projectId = project.id,
        projectNameSnapshot = project.name,
        compensationSnapshot = null,
    )

    /**
     * Applies a requested compensation context to [shift].
     *
     * Falls back to employee-paid work when project time is asked for without a
     * resolvable project: a shift marked as project time with no project would
     * belong to nothing and be paid by nothing.
     */
    fun apply(
        shift: Shift,
        requested: CompensationSource,
        project: Project?,
    ): Shift = when {
        requested.isProjectTime && project != null -> toProjectTime(shift, project)
        else -> toEmployeePaid(shift)
    }

    /**
     * True when moving [shift] to [requested] changes which side pays for it, and
     * so requires the pay figures to be recalculated.
     */
    fun changesCompensationContext(shift: Shift, requested: CompensationSource): Boolean =
        shift.compensationSource != requested
}
