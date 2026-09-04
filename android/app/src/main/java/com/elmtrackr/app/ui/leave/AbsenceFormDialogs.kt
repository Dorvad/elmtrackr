package com.elmtrackr.app.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.model.SickPayBasis
import com.elmtrackr.app.domain.model.VacationPayBasis
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraSemantics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.elmtrackr.app.ui.common.appLocale

/**
 * A calendar-date picker.
 *
 * The date goes in and comes back as UTC midnight, which is what the Material
 * picker works in. An absence is a calendar date rather than an instant, so
 * converting through the device's zone is exactly how a date near midnight ends
 * up a day out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text(stringResource(R.string.leave_form_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.leave_form_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Shows the working behind the estimate.
 *
 * This dialog is the counterweight to presenting a number at all: the basis, the
 * period, the divisor and the tier are all stated, so a figure that disagrees
 * with a payslip can be argued with rather than merely distrusted. When a
 * substituted averaging period was used it says so — that is the one thing a user
 * could not otherwise work out.
 */
@Composable
fun CalculationExplanationDialog(
    days: List<AbsenceDayRow>,
    currencyCode: String,
    onDismiss: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leave_calc_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s10),
            ) {
                val explained = days.mapNotNull { day -> day.snapshot?.let { day to it } }
                if (explained.isEmpty()) {
                    Text(
                        text = stringResource(R.string.leave_gap_no_history),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                explained.forEach { (day, snapshot) ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(
                            text = day.date.format(dateFormat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(basisText(snapshot.payBasis), style = MaterialTheme.typography.bodySmall)
                        if (snapshot.averagePeriodStart != null && snapshot.averagePeriodEnd != null) {
                            Text(
                                text = stringResource(
                                    R.string.leave_calc_period,
                                    snapshot.averagePeriodStart.format(dateFormat),
                                    snapshot.averagePeriodEnd.format(dateFormat),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = auroraSecondaryText(),
                            )
                        }
                        snapshot.averageGrossIncluded?.let { gross ->
                            Text(
                                text = stringResource(
                                    R.string.leave_calc_included,
                                    MoneyFormatter.format(gross, currencyCode, appLocale()),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = auroraSecondaryText(),
                            )
                        }
                        snapshot.averageDivisor?.let { divisor ->
                            Text(
                                text = stringResource(R.string.leave_calc_divisor, formatDivisor(divisor)),
                                style = MaterialTheme.typography.bodySmall,
                                color = auroraSecondaryText(),
                            )
                        }
                        snapshot.sickDayOrdinal?.let { ordinal ->
                            Text(
                                text = stringResource(
                                    R.string.leave_calc_sick_tier,
                                    ordinal,
                                    (snapshot.multiplier * 100).toInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.leave_calc_result,
                                MoneyFormatter.format(snapshot.estimatedGrossPay, currencyCode, appLocale()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (snapshot.usedFallbackAveragePeriod) {
                            Text(
                                text = stringResource(R.string.leave_calc_fallback),
                                style = MaterialTheme.typography.bodySmall,
                                color = auroraSemantics.warningInk,
                            )
                        }
                        if (snapshot.manualOverride?.enabled == true) {
                            Text(
                                text = stringResource(R.string.leave_calc_manual_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = auroraSecondaryText(),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.leave_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = auroraSecondaryText(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.leave_form_cancel)) }
        },
    )
}

/**
 * The persisted basis string, not an enum, because a snapshot may have been
 * written by a build that knew a basis this one does not. An unrecognised basis
 * falls back to the manual wording rather than showing a raw identifier.
 */
@Composable
private fun basisText(payBasis: String): String = stringResource(
    when (payBasis) {
        VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90.persistedValue -> R.string.leave_calc_basis_statutory
        VacationPayBasis.ACTUAL_WORKDAYS_AVERAGE.persistedValue,
        SickPayBasis.HISTORICAL_AVERAGE.persistedValue,
        -> R.string.leave_calc_basis_workdays

        VacationPayBasis.SCHEDULED_HOURS.persistedValue -> R.string.leave_calc_basis_scheduled
        VacationPayBasis.FIXED_DAILY_HOURS.persistedValue -> R.string.leave_calc_basis_fixed
        else -> R.string.leave_calc_basis_manual
    },
)

private fun formatDivisor(divisor: Double): String =
    if (divisor % 1.0 == 0.0) divisor.toInt().toString() else divisor.toString()
