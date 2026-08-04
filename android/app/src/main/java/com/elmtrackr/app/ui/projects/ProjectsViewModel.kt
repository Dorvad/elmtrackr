package com.elmtrackr.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.projects.BillingFormError
import com.elmtrackr.app.domain.projects.BillingFormResult
import com.elmtrackr.app.domain.projects.PaymentFormResult
import com.elmtrackr.app.domain.projects.PaymentRejection
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingFormInput
import com.elmtrackr.app.domain.projects.ProjectBillingFormValidator
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
import com.elmtrackr.app.domain.projects.ProjectPaymentFormValidator
import com.elmtrackr.app.domain.projects.ProjectPaymentRejectedException
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

    /**
     * Bumped by [retry] to rebuild the whole chain.
     *
     * `catch` terminates the flow it catches, so an error here was permanent: the
     * screen rendered an error state with a "Try again" button wired to `{}`, and the
     * only way out was killing the app. Restarting from a nonce is the pattern
     * `ShiftsViewModel` already uses, and it is what makes that button mean something.
     */
    private val refreshNonce = MutableStateFlow(0)

    /** Repository data, before search and filtering are applied. */
    private sealed interface ProjectsData {
        data object Loading : ProjectsData
        data object Disabled : ProjectsData
        data class Ready(val summaries: List<ProjectSummary>, val settings: UserSettings) : ProjectsData
    }

    private val projectsData: Flow<ProjectsData> = refreshNonce
        .flatMapLatest { currentUserProvider.userId.filterNotNull() }
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

    /**
     * Rebuilds the data chain after an error.
     *
     * Every sibling screen has this; Projects was the one that rendered a retry
     * affordance with nothing behind it.
     */
    fun retry() { refreshNonce.value++ }

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
     * Permanent deletion, for any project.
     *
     * No eligibility guard. Deletion used to require an untouched draft, so a
     * project used once could never be removed — only archived — and the archive is
     * not a substitute for deleting your own data. The tracked hours survive as
     * employee-paid work; the billing records and payments do not. The screen states
     * both before asking, which is where the safety now lives.
     */
    fun deleteProject(summary: ProjectSummary, onDeleted: (releasedShifts: Int) -> Unit = {}) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val released = projectsRepository.deleteProject(userId, summary.project.id)
            onDeleted(released)
        }
    }

    // ── Billing ───────────────────────────────────────────────────────────────

    /** Today in the user's work timezone, which is what every due date is judged against. */
    private suspend fun today(): LocalDate {
        val settings = currentUserProvider.currentUserId()?.let { settingsRepository.getSettings(it) }
        return LocalDate.now(settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault())
    }

    /** Form pre-filled from the project's current fee, so accepting it bills what was agreed. */
    suspend fun newBillingInput(project: Project): ProjectBillingFormInput =
        ProjectBillingFormInput.from(project, today())

    private val _billingError = MutableStateFlow<Map<String, BillingFormError>>(emptyMap())
    val billingError: StateFlow<Map<String, BillingFormError>> = _billingError

    private val _paymentRejection = MutableStateFlow<PaymentRejection?>(null)
    val paymentRejection: StateFlow<PaymentRejection?> = _paymentRejection

    fun clearBillingError() { _billingError.value = emptyMap() }

    fun clearPaymentRejection() { _paymentRejection.value = null }

    /**
     * Records billing details for a project — not an invoice. The fee snapshot is
     * built once from the form and stored verbatim; nothing recomputes it from the
     * project afterwards.
     */
    fun recordBilling(input: ProjectBillingFormInput, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            when (val result = ProjectBillingFormValidator.validate(input)) {
                is BillingFormResult.Invalid -> _billingError.value = result.errors
                is BillingFormResult.Valid -> {
                    _billingError.value = emptyMap()
                    val now = Instant.now()
                    projectsRepository.upsertBillingRecord(
                        ProjectBillingRecord(
                            id = "",
                            userId = userId,
                            projectId = input.projectId,
                            fee = result.fee,
                            externalReference = input.externalReference.trim().ifBlank { null },
                            notes = input.notes.trim().ifBlank { null },
                            billedOn = input.billedOn,
                            dueOn = input.dueOn,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    onSaved()
                }
            }
        }
    }

    /**
     * Withdraws a billing record so a corrected one can be recorded.
     *
     * Refused once payments exist: the payment history is the record of money that
     * actually moved, and it must keep pointing at the amounts it settled.
     */
    fun cancelBilling(
        record: ProjectBillingRecord,
        payments: List<ProjectPayment>,
        onBlocked: (ProjectBillingCorrection.Block) -> Unit = {},
    ) {
        viewModelScope.launch {
            val verdict = ProjectBillingCorrection.canCancel(record, payments)
            verdict.blockedReason?.let { return@launch onBlocked(it) }
            projectsRepository.cancelBillingRecord(record.id)
        }
    }

    fun restoreBilling(recordId: String) {
        viewModelScope.launch { projectsRepository.restoreBillingRecord(recordId) }
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    /** Form pre-filled with the outstanding balance — the common case is paying it off. */
    suspend fun newPaymentInput(
        record: ProjectBillingRecord,
        payments: List<ProjectPayment>,
    ): ProjectPaymentFormInput = ProjectPaymentFormInput.forRecord(record, payments, today())

    /**
     * Records or updates a payment. Every rule — currency match, positive amount,
     * no overpayment — is checked here and again in the repository, so a stale
     * screen cannot write a payment the balance will not support.
     */
    fun savePayment(
        input: ProjectPaymentFormInput,
        billingRecord: ProjectBillingRecord?,
        existingPayments: List<ProjectPayment>,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            when (
                val result = ProjectPaymentFormValidator.validate(input, billingRecord, existingPayments)
            ) {
                is PaymentFormResult.NotANumber ->
                    _paymentRejection.value = PaymentRejection.ZeroAmount
                is PaymentFormResult.Rejected -> _paymentRejection.value = result.reason
                is PaymentFormResult.Valid -> {
                    _paymentRejection.value = null
                    val now = Instant.now()
                    runCatching {
                        projectsRepository.upsertPayment(
                            ProjectPayment(
                                id = input.paymentId.orEmpty(),
                                userId = userId,
                                projectId = input.projectId,
                                billingRecordId = input.billingRecordId,
                                paidOn = input.paidOn,
                                amount = result.amount,
                                method = input.method?.name,
                                externalReference = input.externalReference.trim().ifBlank { null },
                                notes = input.notes.trim().ifBlank { null },
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }.onFailure { error ->
                        (error as? ProjectPaymentRejectedException)?.let {
                            _paymentRejection.value = it.reason
                            return@launch
                        }
                        throw error
                    }
                    onSaved()
                }
            }
        }
    }

    /** Deleting recalculates the balance, and therefore the derived status. */
    fun deletePayment(paymentId: String) {
        viewModelScope.launch { projectsRepository.deletePayment(paymentId) }
    }
}
