package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import java.time.Instant
import java.time.LocalDate

/**
 * The work-status actions a project offers, and what each one changes.
 *
 * These move *work* state only. No action here touches a billing record or a
 * payment: completing a project never marks it paid, and paying for one never
 * completes it. Keeping the two apart is the whole reason billing status is
 * derived rather than stored.
 */
enum class ProjectWorkAction {
    SAVE_AS_DRAFT,
    START,
    PAUSE,
    RESUME,
    COMPLETE,
    CANCEL,
    ARCHIVE,
    UNARCHIVE,
    ;

    val targetStatus: ProjectWorkStatus
        get() = when (this) {
            SAVE_AS_DRAFT -> ProjectWorkStatus.DRAFT
            START, RESUME, UNARCHIVE -> ProjectWorkStatus.ACTIVE
            PAUSE -> ProjectWorkStatus.PAUSED
            COMPLETE -> ProjectWorkStatus.COMPLETED
            CANCEL -> ProjectWorkStatus.CANCELLED
            ARCHIVE -> ProjectWorkStatus.ARCHIVED
        }
}

object ProjectWorkStatusActions {

    /**
     * Actions offered for a project in [status]. Only forward-sensible moves are
     * shown, so the UI never needs to disable a visible button.
     */
    fun available(status: ProjectWorkStatus): List<ProjectWorkAction> = when (status) {
        ProjectWorkStatus.DRAFT -> listOf(
            ProjectWorkAction.START,
            ProjectWorkAction.CANCEL,
            ProjectWorkAction.ARCHIVE,
        )
        ProjectWorkStatus.ACTIVE -> listOf(
            ProjectWorkAction.PAUSE,
            ProjectWorkAction.COMPLETE,
            ProjectWorkAction.CANCEL,
            ProjectWorkAction.ARCHIVE,
        )
        ProjectWorkStatus.PAUSED -> listOf(
            ProjectWorkAction.RESUME,
            ProjectWorkAction.COMPLETE,
            ProjectWorkAction.CANCEL,
            ProjectWorkAction.ARCHIVE,
        )
        // A completed project can reopen: money may still be outstanding, and
        // the client may come back with more work.
        ProjectWorkStatus.COMPLETED -> listOf(
            ProjectWorkAction.RESUME,
            ProjectWorkAction.ARCHIVE,
        )
        ProjectWorkStatus.CANCELLED -> listOf(
            ProjectWorkAction.RESUME,
            ProjectWorkAction.ARCHIVE,
        )
        ProjectWorkStatus.ARCHIVED -> listOf(ProjectWorkAction.UNARCHIVE)
    }

    /**
     * Applies [action] to [project].
     *
     * COMPLETE stamps a completion date; reopening clears it, so a reopened
     * project does not claim to have finished. ARCHIVE stamps `archivedAt` and
     * UNARCHIVE clears it, which is what keeps archiving reversible.
     *
     * Nothing about the fee, the tax or any billing record changes.
     */
    fun apply(
        project: Project,
        action: ProjectWorkAction,
        now: Instant,
        today: LocalDate,
    ): Project = when (action) {
        ProjectWorkAction.COMPLETE -> project.copy(
            workStatus = ProjectWorkStatus.COMPLETED,
            completionDate = project.completionDate ?: today,
            updatedAt = now,
        )
        ProjectWorkAction.ARCHIVE -> project.copy(
            workStatus = ProjectWorkStatus.ARCHIVED,
            archivedAt = now,
            updatedAt = now,
        )
        ProjectWorkAction.UNARCHIVE -> project.copy(
            workStatus = ProjectWorkStatus.ACTIVE,
            archivedAt = null,
            updatedAt = now,
        )
        ProjectWorkAction.RESUME, ProjectWorkAction.START -> project.copy(
            workStatus = ProjectWorkStatus.ACTIVE,
            // Reopening a completed or cancelled project drops the completion
            // date; it is no longer finished.
            completionDate = null,
            archivedAt = null,
            updatedAt = now,
        )
        ProjectWorkAction.CANCEL -> project.copy(
            workStatus = ProjectWorkStatus.CANCELLED,
            updatedAt = now,
        )
        ProjectWorkAction.PAUSE -> project.copy(
            workStatus = ProjectWorkStatus.PAUSED,
            updatedAt = now,
        )
        ProjectWorkAction.SAVE_AS_DRAFT -> project.copy(
            workStatus = ProjectWorkStatus.DRAFT,
            updatedAt = now,
        )
    }

    /** Statuses shown in the Projects list, in display order. */
    val listOrder: List<ProjectWorkStatus> = listOf(
        ProjectWorkStatus.ACTIVE,
        ProjectWorkStatus.DRAFT,
        ProjectWorkStatus.PAUSED,
        ProjectWorkStatus.COMPLETED,
        ProjectWorkStatus.CANCELLED,
        ProjectWorkStatus.ARCHIVED,
    )
}
