package com.elmtrackr.app.ui.projects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingFormInput
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.ui.components.states.EmptyState
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.components.states.LoadingState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraStateCrossfade
import com.elmtrackr.app.ui.design.ElmChoiceChip
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraSubScreenTransition
import com.elmtrackr.app.ui.theme.Spacing

/** Where inside the Projects tab the user is. */
private enum class ProjectsDestination { LIST, DETAIL, FORM, BILLING, PAYMENT }

/**
 * The Paid Projects destination: list, detail and form.
 *
 * Navigation inside the tab is local state rather than nested nav destinations,
 * matching how SettingsScreen works, so the tab keeps a single entry in the
 * outer back stack and its position survives rotation via `rememberSaveable`.
 *
 * @param onFormVisibilityChanged hides the bottom bar / rail while the form or
 * detail view is open, as the shift form already does.
 */
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = hiltViewModel(),
    onFormVisibilityChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    var destination by rememberSaveable { mutableStateOf(ProjectsDestination.LIST) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    // Plain remember: these carry a pre-filled form built from data that is
    // re-read on the way in, so restoring a stale copy after process death would
    // show amounts that no longer match the project.
    var billingInput by remember { mutableStateOf<ProjectBillingFormInput?>(null) }
    var paymentInput by remember { mutableStateOf<ProjectPaymentFormInput?>(null) }
    var correctionBlock by remember { mutableStateOf<ProjectBillingCorrection.Block?>(null) }
    val scope = rememberCoroutineScope()

    val ready = state as? ProjectsUiState.Ready
    val selected = selectedProjectId?.let { ready?.summary(it) }

    // A project that disappeared underneath us (deleted, or the feature turned
    // off) must not leave the tab stranded on an empty detail screen.
    LaunchedEffect(ready, destination, selectedProjectId) {
        if (destination == ProjectsDestination.DETAIL && ready != null && selected == null) {
            destination = ProjectsDestination.LIST
            selectedProjectId = null
        }
    }
    LaunchedEffect(destination) {
        onFormVisibilityChanged(destination != ProjectsDestination.LIST)
    }

    BackHandler(enabled = destination != ProjectsDestination.LIST) {
        destination = when (destination) {
            ProjectsDestination.FORM ->
                if (editingProjectId != null) ProjectsDestination.DETAIL else ProjectsDestination.LIST
            // The billing and payment forms are opened from a project, so back
            // returns there rather than all the way out to the list.
            ProjectsDestination.BILLING, ProjectsDestination.PAYMENT -> ProjectsDestination.DETAIL
            else -> ProjectsDestination.LIST
        }
        if (destination == ProjectsDestination.LIST) selectedProjectId = null
    }

    AuroraListScreen {
        AuroraStateCrossfade(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
            contentKey = { current ->
                when (current) {
                    is ProjectsUiState.Loading -> "loading"
                    is ProjectsUiState.Disabled -> "disabled"
                    is ProjectsUiState.Ready -> "ready"
                    is ProjectsUiState.Error -> "error"
                }
            },
        ) { current ->
            when (current) {
                is ProjectsUiState.Loading -> LoadingState()
                // The navigation guard normally redirects before this renders;
                // a benign screen beats a crash if that ever fails.
                is ProjectsUiState.Disabled -> EmptyState(
                    icon = Icons.Outlined.WorkOutline,
                    title = stringResource(R.string.projects_title),
                    subtitle = stringResource(R.string.settings_paid_projects_desc),
                )
                is ProjectsUiState.Error -> ErrorState(message = current.message, onRetry = {})
                is ProjectsUiState.Ready -> AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        auroraSubScreenTransition(targetState.ordinal > initialState.ordinal)
                    },
                    label = "projects-destination",
                ) { dest ->
                    when (dest) {
                        ProjectsDestination.LIST -> ProjectsList(
                            state = current,
                            onQueryChange = viewModel::onQueryChange,
                            onFilterChange = viewModel::onFilterChange,
                            onCreate = {
                                editingProjectId = null
                                destination = ProjectsDestination.FORM
                            },
                            onOpen = { projectId ->
                                selectedProjectId = projectId
                                destination = ProjectsDestination.DETAIL
                            },
                        )

                        ProjectsDestination.DETAIL -> {
                            val summary = selectedProjectId?.let { current.summary(it) }
                            if (summary == null) {
                                LoadingState()
                            } else {
                                val projectId = summary.project.id
                                val billingRecords by remember(projectId) {
                                    viewModel.billingRecordsFor(projectId)
                                }.collectAsState(initial = emptyList())
                                val payments by remember(projectId) {
                                    viewModel.paymentsFor(projectId)
                                }.collectAsState(initial = emptyList())
                                ProjectDetailScreen(
                                    summary = summary,
                                    billingRecords = billingRecords,
                                    payments = payments,
                                    onRecordBilling = {
                                        scope.launch {
                                            viewModel.clearBillingError()
                                            billingInput = viewModel.newBillingInput(summary.project)
                                            destination = ProjectsDestination.BILLING
                                        }
                                    },
                                    onRecordPayment = { record ->
                                        scope.launch {
                                            viewModel.clearPaymentRejection()
                                            paymentInput = viewModel.newPaymentInput(record, payments)
                                            destination = ProjectsDestination.PAYMENT
                                        }
                                    },
                                    onCancelBilling = { record ->
                                        viewModel.cancelBilling(record, payments) { blocked ->
                                            correctionBlock = blocked
                                        }
                                    },
                                    onRestoreBilling = viewModel::restoreBilling,
                                    onDeletePayment = viewModel::deletePayment,
                                    correctionBlock = correctionBlock,
                                    onDismissCorrectionBlock = { correctionBlock = null },
                                    onEdit = {
                                        editingProjectId = projectId
                                        destination = ProjectsDestination.FORM
                                    },
                                    onWorkAction = { action ->
                                        viewModel.applyWorkAction(summary.project, action)
                                    },
                                    onDelete = {
                                        viewModel.deleteProject(summary) {
                                            selectedProjectId = null
                                            destination = ProjectsDestination.LIST
                                        }
                                    },
                                    onBack = {
                                        selectedProjectId = null
                                        destination = ProjectsDestination.LIST
                                    },
                                )
                            }
                        }

                        ProjectsDestination.BILLING -> {
                            val input = billingInput
                            if (input == null) {
                                LoadingState()
                            } else {
                                val billingErrors by viewModel.billingError.collectAsState()
                                ProjectBillingFormScreen(
                                    initialInput = input,
                                    errors = billingErrors,
                                    onSave = { edited ->
                                        viewModel.recordBilling(edited) {
                                            destination = ProjectsDestination.DETAIL
                                        }
                                    },
                                    onBack = { destination = ProjectsDestination.DETAIL },
                                )
                            }
                        }

                        ProjectsDestination.PAYMENT -> {
                            val input = paymentInput
                            val summary = selectedProjectId?.let { current.summary(it) }
                            val projectId = summary?.project?.id
                            val records by remember(projectId) {
                                projectId?.let { viewModel.billingRecordsFor(it) }
                                    ?: kotlinx.coroutines.flow.flowOf(emptyList())
                            }.collectAsState(initial = emptyList())
                            val payments by remember(projectId) {
                                projectId?.let { viewModel.paymentsFor(it) }
                                    ?: kotlinx.coroutines.flow.flowOf(emptyList())
                            }.collectAsState(initial = emptyList())
                            val record = input?.billingRecordId?.let { id -> records.firstOrNull { it.id == id } }
                            if (input == null || record == null) {
                                LoadingState()
                            } else {
                                val rejection by viewModel.paymentRejection.collectAsState()
                                ProjectPaymentFormScreen(
                                    initialInput = input,
                                    billingRecord = record,
                                    existingPayments = payments,
                                    rejection = rejection,
                                    onSave = { edited ->
                                        viewModel.savePayment(edited, record, payments) {
                                            destination = ProjectsDestination.DETAIL
                                        }
                                    },
                                    onBack = { destination = ProjectsDestination.DETAIL },
                                )
                            }
                        }

                        ProjectsDestination.FORM -> {
                            val editing: Project? = editingProjectId?.let { current.summary(it)?.project }
                            val editingSummary = editingProjectId?.let { current.summary(it) }
                            ProjectFormScreen(
                                existing = editing,
                                initialInput = editing
                                    ?.let { ProjectFormInput.forEdit(it) }
                                    ?: viewModel.newProjectInput(current.settings),
                                isBilled = editingSummary?.isBilled == true,
                                isSaving = current.isSaving,
                                onSave = { input ->
                                    viewModel.saveProject(input, editing) { savedId ->
                                        selectedProjectId = savedId
                                        editingProjectId = null
                                        destination = ProjectsDestination.DETAIL
                                    }
                                },
                                onBack = {
                                    destination = if (editingProjectId != null) {
                                        ProjectsDestination.DETAIL
                                    } else {
                                        ProjectsDestination.LIST
                                    }
                                    editingProjectId = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Internal rather than private so JVM render tests can drive it directly. */
@Composable
internal fun ProjectsList(
    state: ProjectsUiState.Ready,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ProjectStatusFilter) -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.projects_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(R.string.projects_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.projects_search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.projects_search_clear),
                            )
                        }
                    }
                },
            )
        }

        item {
            // Horizontally scrollable chip row: six statuses will not fit on a
            // phone, and wrapping them costs a whole extra line of height.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ProjectStatusFilter.entries.forEach { filter ->
                    val count = state.counts[filter] ?: 0
                    // Hide empty status views, except the default and the one in use.
                    if (count > 0 || filter == ProjectStatusFilter.ALL || filter == state.filter) {
                        ElmChoiceChip(
                            selected = state.filter == filter,
                            onClick = { onFilterChange(filter) },
                        ) {
                            Text(
                                text = "${stringResource(ProjectLabels.statusFilter(filter))} ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        item {
            ElmGradientButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.projects_create), fontWeight = FontWeight.Bold)
            }
        }

        when {
            state.hasNoProjects -> item {
                EmptyState(
                    icon = Icons.Outlined.WorkOutline,
                    title = stringResource(R.string.projects_empty_title),
                    subtitle = stringResource(R.string.projects_empty_subtitle),
                )
            }
            state.hasNoMatches -> item {
                EmptyState(
                    icon = Icons.Outlined.WorkOutline,
                    title = stringResource(R.string.projects_no_matches_title),
                    subtitle = stringResource(R.string.projects_no_matches_subtitle),
                )
            }
            else -> items(state.visibleProjects, key = { it.project.id }) { summary ->
                ProjectCard(summary = summary, onClick = { onOpen(summary.project.id) })
            }
        }

        item {
            Box(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                ProjectNoteText(stringResource(R.string.projects_footer_note))
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
