package com.elmtrackr.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraHair
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import java.util.TimeZone

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit                    = {},
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState is OnboardingUiState.Completed) {
        onCompleted()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (uiState) {
            is OnboardingUiState.Saving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuroraIndigo)
            }
            else -> OnboardingForm(
                errors     = (uiState as? OnboardingUiState.ValidationError)?.errors ?: emptyMap(),
                onComplete = viewModel::completeOnboarding,
            )
        }
    }
}

@Composable
private fun OnboardingForm(
    errors: Map<String, String>,
    onComplete: (OnboardingInput) -> Unit,
) {
    var displayName         by rememberSaveable { mutableStateOf("") }
    var timezone            by rememberSaveable { mutableStateOf(TimeZone.getDefault().id) }
    var dailyOTHours        by rememberSaveable { mutableIntStateOf(8) }
    var weeklyOTHours       by rememberSaveable { mutableIntStateOf(40) }
    var weekendDays         by rememberSaveable { mutableStateOf(setOf(5, 6)) }
    var rateText            by rememberSaveable { mutableStateOf("") }
    var featuresTravelRefunds by rememberSaveable { mutableStateOf(false) }
    var featuresPaidProjects  by rememberSaveable { mutableStateOf(false) }
    var featuresInsights      by rememberSaveable { mutableStateOf(true) }
    var featuresClockStyles   by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier            = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(0f to AuroraIndigo, 0.5f to AuroraPlum, 1f to AuroraAqua),
                        ),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "ElmTrackr",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Let's set up your workspace",
                style = MaterialTheme.typography.bodyLarge,
                color = AuroraInk2,
            )

            Spacer(Modifier.height(32.dp))

            // ── About You ─────────────────────────────────────────────────────
            ElmSectionHeader("About You")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Display name (optional)") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Next,
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = timezone,
                onValueChange = { timezone = it },
                label         = { Text("Timezone") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            SectionDivider()

            // ── Work Schedule ─────────────────────────────────────────────────
            ElmSectionHeader("Work Schedule")
            Spacer(Modifier.height(12.dp))
            NumberStepRow(
                label       = "Daily overtime threshold",
                value       = dailyOTHours,
                unit        = "h/day",
                min         = 1,
                onDecrement = { if (dailyOTHours > 1) dailyOTHours-- },
                onIncrement = { dailyOTHours++ },
            )
            errors["dailyOT"]?.let { FieldError(it) }
            Spacer(Modifier.height(8.dp))
            NumberStepRow(
                label       = "Weekly overtime threshold",
                value       = weeklyOTHours,
                unit        = "h/week",
                min         = 1,
                onDecrement = { if (weeklyOTHours > 1) weeklyOTHours-- },
                onIncrement = { weeklyOTHours++ },
            )
            errors["weeklyOT"]?.let { FieldError(it) }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Weekend days", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            DaySelector(
                selected = weekendDays,
                onToggle = { day ->
                    weekendDays = if (day in weekendDays) weekendDays - day else weekendDays + day
                },
            )

            SectionDivider()

            // ── Earnings ──────────────────────────────────────────────────────
            ElmSectionHeader("Earnings (optional)")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = rateText,
                onValueChange = { rateText = it },
                label         = { Text("Hourly rate") },
                placeholder   = { Text("e.g. 50.00") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction    = ImeAction.Next,
                ),
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )
            errors["hourlyRate"]?.let { FieldError(it) }

            SectionDivider()

            // ── Features ──────────────────────────────────────────────────────
            ElmSectionHeader("Features")
            Spacer(Modifier.height(12.dp))
            FeatureToggleRow("Travel refund claims", "Track and claim travel expenses", featuresTravelRefunds) { featuresTravelRefunds = it }
            FeatureToggleRow("Paid projects",        "Assign shifts to client projects", featuresPaidProjects)  { featuresPaidProjects = it }
            FeatureToggleRow("Insights",             "Earnings trends and overtime alerts", featuresInsights)  { featuresInsights = it }
            FeatureToggleRow("Clock styles",         "Customise the clock button appearance", featuresClockStyles) { featuresClockStyles = it }

            errors["save"]?.let {
                Spacer(Modifier.height(12.dp))
                FieldError(it)
            }

            Spacer(Modifier.height(32.dp))

            ElmGradientButton(
                onClick = {
                    onComplete(
                        OnboardingInput(
                            displayName          = displayName.trim(),
                            timezone             = timezone.ifBlank { TimeZone.getDefault().id },
                            dailyOvertimeHours   = dailyOTHours,
                            weeklyOvertimeHours  = weeklyOTHours,
                            weekendDays          = weekendDays.sorted(),
                            hourlyRate           = rateText.toDoubleOrNull(),
                            featuresTravelRefunds = featuresTravelRefunds,
                            featuresPaidProjects  = featuresPaidProjects,
                            featuresInsights      = featuresInsights,
                            featuresClockStyles   = featuresClockStyles,
                        )
                    )
                },
            ) {
                Text("Get started", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = AuroraHair)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun FieldError(message: String) {
    Spacer(Modifier.height(2.dp))
    Text(
        text     = message,
        style    = MaterialTheme.typography.bodySmall,
        color    = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberStepRow(
    label: String,
    value: Int,
    unit: String,
    min: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDecrement, enabled = value > min) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text       = "$value $unit",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(onClick = onIncrement) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DaySelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 3).forEach { day ->
                FilterChip(
                    selected  = day in selected,
                    onClick   = { onToggle(day) },
                    label     = { Text(DAY_LABELS[day]) },
                    modifier  = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4, 5, 6).forEach { day ->
                FilterChip(
                    selected  = day in selected,
                    onClick   = { onToggle(day) },
                    label     = { Text(DAY_LABELS[day]) },
                    modifier  = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = label,       style = MaterialTheme.typography.bodyLarge)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = AuroraInk2)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    ElmTrackrTheme {
        OnboardingForm(errors = emptyMap(), onComplete = {})
    }
}
