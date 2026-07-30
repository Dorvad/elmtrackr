package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus

/**
 * One selectable project in the clock-in picker. Carries the client and currency
 * because two projects can share a name across clients, and the currency tells
 * the user which project's money they are about to affect.
 */
data class ProjectClockInOption(
    val projectId: String,
    val name: String,
    val clientName: String?,
    val currencyCode: String,
)

/**
 * Which projects can be clocked into, and what the clock-in button should do.
 *
 * Pure, so the dashboard, the shift edit form and any future surface agree on
 * what "clockable" means without each re-deriving it.
 */
object ProjectClockInOptions {

    /**
     * Statuses a user can track time against: work under way, running or paused.
     * Completed, cancelled and archived projects are excluded — logging new hours
     * against them would silently change a finished project's effective rate, and
     * against a billed one it would contradict the snapshot the client was billed
     * from.
     *
     * DRAFT is excluded too: a draft has no agreed fee yet, so any effective rate
     * derived from its hours would be based on a number the user has not settled.
     */
    val clockableStatuses: Set<ProjectWorkStatus> = setOf(
        ProjectWorkStatus.ACTIVE,
        ProjectWorkStatus.PAUSED,
    )

    fun isClockable(project: Project): Boolean =
        project.workStatus in clockableStatuses && project.archivedAt == null

    /** Clockable projects, most recently touched first. */
    fun options(projects: List<Project>): List<ProjectClockInOption> =
        projects.asSequence()
            .filter(::isClockable)
            .sortedWith(compareByDescending<Project> { it.updatedAt }.thenBy { it.name.lowercase() })
            .map {
                ProjectClockInOption(
                    projectId = it.id,
                    name = it.name,
                    clientName = it.clientName,
                    currencyCode = it.currencyCode,
                )
            }
            .toList()

    /**
     * Resolves what to actually clock in, given what the user picked.
     *
     * Falls back to employee-paid hourly work whenever project time is not
     * genuinely available: the feature is off, no project is selectable, or the
     * selected project is no longer clockable. Clocking in as project time with
     * no project attached would produce unpaid hours belonging to nothing.
     */
    fun resolve(
        paidProjectsEnabled: Boolean,
        requested: CompensationSource,
        selectedProjectId: String?,
        projects: List<Project>,
    ): Selection {
        if (!paidProjectsEnabled || requested.isEmployeePaid) return Selection.employee()
        val project = projects.firstOrNull { it.id == selectedProjectId && isClockable(it) }
            ?: return Selection.employee()
        return Selection(
            compensationSource = CompensationSource.PROJECT,
            projectId = project.id,
            projectNameSnapshot = project.name,
        )
    }

    data class Selection(
        val compensationSource: CompensationSource,
        val projectId: String?,
        val projectNameSnapshot: String?,
    ) {
        companion object {
            fun employee() = Selection(CompensationSource.EMPLOYEE, null, null)
        }
    }
}
