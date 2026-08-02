package com.elmtrackr.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.review.PlayReviewFlowLauncher
import com.elmtrackr.app.review.ReviewPromptCoordinator
import com.elmtrackr.app.review.ReviewPromptInspection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Developer-only view into the review-prompt eligibility decision. Strictly
 * read-only against prompt state: [ReviewPromptCoordinator.inspectEligibility]
 * records nothing, and the test-launch button talks straight to Play without
 * touching the attempt cooldown, so poking around here never changes what a
 * production user would see.
 */
@HiltViewModel
class ReviewPromptDebugViewModel @Inject constructor(
    private val coordinator: ReviewPromptCoordinator,
) : ViewModel() {

    private val _inspection = MutableStateFlow<ReviewPromptInspection?>(null)
    val inspection: StateFlow<ReviewPromptInspection?> = _inspection

    fun refresh() {
        viewModelScope.launch { _inspection.value = coordinator.inspectEligibility() }
    }

    fun testLaunchReviewFlow(activity: Activity) {
        viewModelScope.launch { PlayReviewFlowLauncher.launch(activity) }
    }
}

@Composable
internal fun ReviewPromptDebugCard(
    viewModel: ReviewPromptDebugViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val inspection by viewModel.inspection.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsSectionCard(title = "REVIEW PROMPT (DEBUG)") {
        val current = inspection
        if (current == null) {
            Text("Loading eligibility…", style = MaterialTheme.typography.bodySmall)
        } else {
            val inputs = current.inputs
            val verdict = current.verdict
            SettingsInfoRow("Eligible now", if (verdict.eligible) "Yes" else "No")
            SettingsInfoRow("Strong milestone", if (verdict.hasStrongMilestone) "Yes" else "No")
            SettingsInfoRow("Valid shifts", "${inputs.validShiftCount}")
            SettingsInfoRow("Usage days", "${inputs.usageDayCount}")
            SettingsInfoRow("Monthly report viewed", debugTimestamp(inputs.monthlyReportViewedAt))
            SettingsInfoRow("Report exported", debugTimestamp(inputs.reportExportedAt))
            SettingsInfoRow("First used", debugTimestamp(inputs.firstUsedAt))
            SettingsInfoRow("Last clock-in", debugTimestamp(inputs.lastClockInAt))
            SettingsInfoRow("Last negative event", debugTimestamp(inputs.lastDiscouragingEventAt))
            SettingsInfoRow("Last attempt", debugTimestamp(inputs.lastAttemptAt))
            SettingsInfoRow("Onboarding active", if (inputs.onboardingActive) "Yes" else "No")
            SettingsInfoRow("Shift active", if (inputs.shiftActive) "Yes" else "No")
            if (verdict.blockers.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Blockers: ${verdict.blockers.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { (context as? Activity)?.let(viewModel::testLaunchReviewFlow) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test-launch review flow (no state change)")
        }
    }
}

private fun debugTimestamp(atMs: Long?): String =
    atMs?.let { Instant.ofEpochMilli(it).toString() } ?: "never"
