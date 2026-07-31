package com.elmtrackr.app.ui.projects

import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.domain.projects.ProjectSummary

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState

    /**
     * The Paid Projects module is off. The navigation guard normally redirects
     * before this renders; it exists so no project data can appear if the screen
     * is ever composed while the feature is disabled.
     */
    data object Disabled : ProjectsUiState

    data class Ready(
        /** Everything, unfiltered — the detail screen and counts read from this. */
        val allProjects: List<ProjectSummary> = emptyList(),
        /** What the list shows for the current query and filter. */
        val visibleProjects: List<ProjectSummary> = emptyList(),
        val query: String = "",
        val filter: ProjectStatusFilter = ProjectStatusFilter.ALL,
        val counts: Map<ProjectStatusFilter, Int> = emptyMap(),
        val settings: UserSettings? = null,
        val isSaving: Boolean = false,
    ) : ProjectsUiState {
        /** True when the user has no projects at all, as opposed to none matching. */
        val hasNoProjects: Boolean get() = allProjects.isEmpty()

        /** True when a search or filter hid everything. */
        val hasNoMatches: Boolean get() = allProjects.isNotEmpty() && visibleProjects.isEmpty()

        fun summary(projectId: String): ProjectSummary? =
            allProjects.firstOrNull { it.project.id == projectId }
    }

    data class Error(val message: String) : ProjectsUiState
}
