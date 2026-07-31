package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.projects.ProjectClockInOption
import com.elmtrackr.app.domain.projects.ProjectShiftProjectionCalculator
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.text.BidiText
import kotlinx.coroutines.delay
import java.time.Instant
import com.elmtrackr.app.ui.projects.formatted
import com.elmtrackr.app.ui.projects.formattedMinutes
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Chooses whether the next clock-in is hourly work or project time.
 *
 * Only rendered when Paid Projects is on and at least one project is open for
 * time tracking; with the feature off, the dashboard looks exactly as it always
 * did and every punch is hourly work.
 */
@Composable
fun ProjectTimeSelector(
    options: List<ProjectClockInOption>,
    selectedSource: CompensationSource,
    selectedProjectId: String?,
    note: String,
    onSelectSource: (CompensationSource) -> Unit,
    onSelectProject: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.project_time_source_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            val sources = listOf(CompensationSource.EMPLOYEE, CompensationSource.PROJECT)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sources.forEachIndexed { index, source ->
                    SegmentedButton(
                        selected = selectedSource == source,
                        onClick = { onSelectSource(source) },
                        shape = SegmentedButtonDefaults.itemShape(index, sources.size),
                        label = {
                            Text(
                                stringResource(
                                    when (source) {
                                        CompensationSource.EMPLOYEE -> R.string.project_time_source_hourly
                                        CompensationSource.PROJECT -> R.string.project_time_source_project
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Text(
                text = stringResource(
                    when (selectedSource) {
                        CompensationSource.EMPLOYEE -> R.string.project_time_source_hourly_desc
                        CompensationSource.PROJECT -> R.string.project_time_source_project_desc
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedSource.isProjectTime) {
                Text(
                    text = stringResource(R.string.project_time_pick_project),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.project_time_pick_project_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(options, key = { it.projectId }) { option ->
                        FilterChip(
                            selected = option.projectId == selectedProjectId,
                            onClick = { onSelectProject(option.projectId) },
                            label = {
                                Column {
                                    Text(option.name, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = option.clientName
                                            ?.let {
                                                stringResource(
                                                    R.string.project_time_client_currency,
                                                    BidiText.isolate(it),
                                                    BidiText.isolate(option.currencyCode),
                                                )
                                            }
                                            ?: BidiText.isolate(option.currencyCode),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(R.string.project_time_note)) },
                    supportingText = { Text(stringResource(R.string.project_time_note_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Live figures for a project shift in progress.
 *
 * Deliberately shows no wage: this time earns the project's fee, not hourly pay.
 * A rate reads "not enough hours yet" rather than zero when the project has no
 * banked time, because zero would read as "you are earning nothing".
 */
@Composable
fun ActiveProjectShiftCard(
    project: Project,
    activeShift: Shift,
    projectShifts: List<Shift>,
    featureDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // The projected rate and budget move as the shift runs. A minute is the
    // finest resolution any of these figures is shown at, so the card re-projects
    // once a minute rather than on the per-second elapsed-time timer.
    var now by remember(activeShift.id) { mutableStateOf(Instant.now()) }
    LaunchedEffect(activeShift.id) {
        while (true) {
            now = Instant.now()
            delay(PROJECTION_TICK_MILLIS)
        }
    }
    val projection = remember(project, activeShift, projectShifts, now) {
        ProjectShiftProjectionCalculator.project(project, activeShift, projectShifts, now)
    }
    val projectName = project.name
    val targetHourlyRate = project.targetHourlyRate

    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.project_active_title, BidiText.isolate(projectName)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.project_time_shift_unpaid_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                RateFigure(
                    label = stringResource(R.string.project_active_current_rate),
                    rate = projection.currentHourlyRate,
                    modifier = Modifier.weight(1f),
                )
                RateFigure(
                    label = stringResource(R.string.project_active_projected_rate),
                    rate = projection.projectedHourlyRate,
                    modifier = Modifier.weight(1f),
                )
            }

            projection.budgetRemainingMinutes?.let { remaining ->
                Text(
                    text = if (remaining < 0) {
                        stringResource(R.string.project_active_budget_over, formattedMinutes(-remaining))
                    } else {
                        stringResource(R.string.project_active_budget_left, formattedMinutes(remaining))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remaining < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Only shown when the user set a target rate. Without one there is
            // nothing to count down to, so no break-even message is invented.
            if (targetHourlyRate != null) {
                projection.minutesUntilTargetRate?.let { remaining ->
                    Text(
                        text = if (remaining <= 0) {
                            stringResource(
                                R.string.project_active_target_passed,
                                targetHourlyRate.formatted(),
                            )
                        } else {
                            stringResource(
                                R.string.project_active_target_left,
                                formattedMinutes(remaining),
                                targetHourlyRate.formatted(),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (featureDisabled) {
                Text(
                    text = stringResource(R.string.project_active_feature_off, projectName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A minute: the finest resolution any figure on this card is shown at. */
private const val PROJECTION_TICK_MILLIS = 60_000L

@Composable
private fun RateFigure(label: String, rate: Money?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = rate?.formatted() ?: stringResource(R.string.project_active_no_rate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * What a finished project shift did to its project.
 *
 * Informational only: it reports the hours added and the resulting effective
 * rate, and never a wage — a project shift earns the project's fee.
 */
@Composable
fun ProjectShiftSummaryCard(
    summary: DashboardViewModel.ProjectShiftSummary,
    onDismiss: () -> Unit,
    onViewProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(
                    R.string.project_shift_done_title,
                    formattedMinutes(summary.addedMinutes),
                    BidiText.isolate(summary.projectName),
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = summary.effectiveHourlyRate
                    ?.let { stringResource(R.string.project_shift_done_rate, it.formatted()) }
                    ?: stringResource(R.string.project_shift_done_no_rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onViewProject) {
                    Text(stringResource(R.string.project_shift_done_view))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.project_shift_done_dismiss))
                }
            }
        }
    }
}

/**
 * Notice for a project shift that is still running after Paid Projects was
 * switched off. The shift is not converted and not discarded — it stays project
 * time and can be clocked out normally.
 */
@Composable
fun DisabledProjectShiftNotice(
    projectName: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = projectName
                ?.let { stringResource(R.string.project_active_feature_off, it) }
                ?: stringResource(R.string.project_active_feature_off_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}
