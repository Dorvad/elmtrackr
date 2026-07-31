package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectWorkStatus

/** Status filter on the Projects list. */
enum class ProjectStatusFilter {
    /** Everything except archived — the default view. */
    ALL,
    ACTIVE,
    DRAFT,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ARCHIVED,
    ;

    val workStatus: ProjectWorkStatus?
        get() = when (this) {
            ALL -> null
            ACTIVE -> ProjectWorkStatus.ACTIVE
            DRAFT -> ProjectWorkStatus.DRAFT
            PAUSED -> ProjectWorkStatus.PAUSED
            COMPLETED -> ProjectWorkStatus.COMPLETED
            CANCELLED -> ProjectWorkStatus.CANCELLED
            ARCHIVED -> ProjectWorkStatus.ARCHIVED
        }
}

/**
 * Search and filtering for the Projects list.
 *
 * Pure so both behaviours are testable without a ViewModel, and so the list and
 * any future project picker agree on what "archived is hidden by default" means.
 */
object ProjectListFilter {

    /**
     * @param query matched case-insensitively against project name, client name
     * and description. Blank matches everything.
     * @param filter [ProjectStatusFilter.ALL] hides archived projects: they are
     * history, not work in progress. Selecting ARCHIVED explicitly shows them.
     */
    fun apply(
        summaries: List<ProjectSummary>,
        query: String,
        filter: ProjectStatusFilter,
    ): List<ProjectSummary> {
        val byStatus = when (val status = filter.workStatus) {
            null -> summaries.filterNot { it.project.workStatus == ProjectWorkStatus.ARCHIVED }
            else -> summaries.filter { it.project.workStatus == status }
        }
        return byStatus.filter { matches(it, query) }
    }

    fun matches(summary: ProjectSummary, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val project = summary.project
        return project.name.lowercase().contains(needle) ||
            project.clientName?.lowercase()?.contains(needle) == true ||
            project.description?.lowercase()?.contains(needle) == true
    }

    /** Count per filter chip, so the UI can show which views have anything in them. */
    fun counts(summaries: List<ProjectSummary>): Map<ProjectStatusFilter, Int> =
        ProjectStatusFilter.entries.associateWith { filter ->
            apply(summaries, query = "", filter = filter).size
        }

    /**
     * Display order: work in progress first, then history, and within a group
     * the most recently touched first.
     */
    fun sorted(summaries: List<ProjectSummary>): List<ProjectSummary> =
        summaries.sortedWith(
            compareBy<ProjectSummary> {
                ProjectWorkStatusActions.listOrder.indexOf(it.project.workStatus)
                    .takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenByDescending { it.project.updatedAt }
                .thenBy { it.project.name.lowercase() },
        )
}
