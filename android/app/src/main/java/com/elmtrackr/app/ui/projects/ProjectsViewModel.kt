package com.elmtrackr.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectFormValidator
import com.elmtrackr.app.domain.projects.ProjectListFilter
import com.elmtrackr.app.domain.projects.ProjectMetrics
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.domain.projects.ProjectSummary
import com.elmtrackr.app.domain.projects.ProjectTimeSummary
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.domain.projects.ProjectWorkStatusActions
import com.elmtrackr.app.domain.repository.ProjectsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectsRepository: ProjectsRepository,
    private val settingsRepository: SettingsRepository,
    private val shiftsRepository: ShiftsRepository,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ProjectStatusFilter.ALL)
    private val isSaving = MutableStateFlow(false)

    /** Repository data, before search and filtering are applied. */
    private sealed interface ProjectsData {
        data object Loading : ProjectsData
        data object Disabled : ProjectsData
        data class Ready(val summaries: List<ProjectSummary>, val settings: UserSettings) : ProjectsData
    }

    private val projectsData: Flow<ProjectsData> = currentUserProvider.userId
        .filterNotNull()
        .flatMapLatest { userId ->
            combine(
                settingsRepository.observeSettings(userId),
                projectsRepository.observeProjects(userId),
                shiftsRepository.observeShifts(userId),
                projectsRepository.observeAllBillingRecords(userId),
                projectsRepository.observeAllPayments(userId),
            ) { settings, projects, shifts, billingRecords, payments ->
                when {
                    settings == null -> ProjectsData.Loading
                    // Belt and braces alongside the navigation guard: with the
                    // module off, no project data leaves this ViewModel.
                    !settings.featuresPaidProjects -> ProjectsData.Disabled
                    else -> {
                        val today = LocalDate.now(WorkTimezone.zoneFor(settings))
                        val timeByProject = ProjectMetrics.timeSummaries(shifts)
                        ProjectsData.Ready(
                            summaries = ProjectListFilter.sorted(
                                projects.map { project ->
                                    ProjectMetrics.summarize(
                                        project = project,
                                        shifts = shifts,
                                        billingRecords = billingRecords,
                                        payments = payments,
                                        today = today,
                                        timeSummary = timeByProject[project.id] ?: ProjectTimeSummary(),
                                    )
                                },
                            ),
                            settings = settings,
                        )
                    }
                }
            }
        }

    val uiState: StateFlow<ProjectsUiState> =
        combine(projectsData, query, filter, isSaving) { data, currentQuery, currentFilter, saving ->
            when (data) {
                ProjectsData.Loading -> ProjectsUiState.Loading
                ProjectsData.Disabled -> ProjectsUiState.Disabled
                is ProjectsData.Ready -> ProjectsUiState.Ready(
                    allProjects = data.summaries,
                    visibleProjects = ProjectListFilter.apply(
                        data.summaries,
                        currentQuery,
                        currentFilter,
                    ),
                    query = currentQuery,
                    filter = currentFilter,
                    counts = ProjectListFilter.counts(data.summaries),
                    settings = data.settings,
                    isSaving = saving,
                )
            }
        }
            .catch { error -> emit(ProjectsUiState.Error(error.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ProjectsUiState.Loading,
            )

    /** Billing records for one project, for the detail screen. */
    fun billingRecordsFor(projectId: String): Flow<List<com.elmtrackr.app.domain.model.ProjectBillingRecord>> =
        projectsRepository.observeBillingRecords(projectId)

    /** Payments for one project, for the detail screen. */
    fun paymentsFor(projectId: String): Flow<List<com.elmtrackr.app.domain.model.ProjectPayment>> =
        projectsRepository.observePayments(projectId)

    fun onQueryChange(value: String) { query.value = value }

    fun onFilterChange(value: ProjectStatusFilter) { filter.value = value }

    /** Blank form seeded from the user's Paid Projects defaults. */
    fun newProjectInput(settings: UserSettings?): ProjectFormInput =
        ProjectFormInput.forNewProject(settings)

    /**
     * Creates or updates a project. Deliberately writes nothing to billing
     * records or payments: editing a project's price must leave an existing
     * billing snapshot exactly as it was billed.
     */
    fun saveProject(input: ProjectFormInput, existing: Project?, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val project = ProjectFormValidator.toProject(input, existing, userId, Instant.now())
                ?: return@launch
            isSaving.value = true
            try {
                val saved = projectsRepository.upsertProject(project)
                onSaved(saved.id)
            } finally {
                isSaving.value = false
            }
        }
    }

    fun applyWorkAction(project: Project, action: ProjectWorkAction) {
        viewModelScope.launch {
            val settings = currentUserProvider.currentUserId()
                ?.let { settingsRepository.getSettings(it) }
            val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
            projectsRepository.upsertProject(
                ProjectWorkStatusActions.apply(project, action, Instant.now(), LocalDate.now(zone)),
            )
        }
    }

    /**
     * Permanent deletion, only for a project that has never been used. The guard
     * is re-checked here rather than trusted from the UI, so a stale screen
     * cannot delete a project that has since been billed or tracked against.
     */
    fun deleteProject(summary: ProjectSummary, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            if (!summary.canDeletePermanently) return@launch
            val userId = currentUserProvider.currentUserId() ?: return@launch
            projectsRepository.deleteProject(userId, summary.project.id)
            onDeleted()
        }
    }
}
