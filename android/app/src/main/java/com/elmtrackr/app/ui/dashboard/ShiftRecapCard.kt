package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.ui.common.LocalWorkZone
import com.elmtrackr.app.ui.common.PayComponentList
import com.elmtrackr.app.ui.common.PayComponents
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.durationText
import com.elmtrackr.app.ui.common.paidTimeNote
import com.elmtrackr.app.ui.common.visibleRows
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * The conclusion of a shift: what it lasted, what was worked, what is paid, and what
 * it is estimated to earn — from the same engine every other screen uses.
 *
 * Only values the engine actually produced are shown. A break row appears when there
 * was a break, a paid-time row only when paid time differs from worked time, and the
 * money only when the shift's profile has a rate. Without one the card says how to
 * get an estimate instead of printing a zero that looks like a result.
 *
 * Deliberately says nothing about the cloud. The shift is saved on this device when
 * this appears; whether it has reached the server is the sync screen's question, and
 * "saved" here must not be read as "backed up".
 */
@Composable
internal fun ShiftRecapCard(
    recap: DashboardViewModel.ShiftRecap,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onSetRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    val zone = LocalWorkZone.current
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val time = recap.time
    val pay = recap.pay

    ElmCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.s6),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_recap_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(
                            R.string.dashboard_recap_time_range,
                            recap.startTime.atZone(zone).format(timeFormatter),
                            recap.endTime.atZone(zone).format(timeFormatter),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.dashboard_recap_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.s18),
                    )
                }
            }

            if (time != null) {
                RecapRow(stringResource(R.string.paid_time_elapsed), durationText(time.elapsedMinutes))
                if (time.unpaidBreakMinutes > 0) {
                    RecapRow(stringResource(R.string.paid_time_unpaid_break), durationText(time.unpaidBreakMinutes))
                }
                if (time.paidBreakMinutes > 0) {
                    RecapRow(stringResource(R.string.paid_time_paid_break), durationText(time.paidBreakMinutes))
                }
                RecapRow(
                    stringResource(R.string.paid_time_worked),
                    durationText(time.workedMinutes),
                    emphasized = true,
                )
                if (time.payableDiffersFromWorked) {
                    val paid = durationText(time.payableMinutes)
                    RecapRow(stringResource(R.string.paid_time_paid), paid, emphasized = true)
                    paidTimeNote(paid, time.reasons)?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (pay != null) {
                val components = PayComponents.of(pay)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.dashboard_recap_estimated_pay),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.dashboard_recap_before_tax),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        MoneyFormatter.format(pay.totalGross, pay.currencyCode, locale),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                // The split only earns its space when there is more than one kind of
                // pay in it; a regular-only shift is fully described by its total.
                if (components.visibleRows().size > 1) {
                    // The total is the headline just above; repeating it as a row
                    // read as a second, separate figure.
                    PayComponentList(components, showDeductions = false, showTotal = false)
                }
            } else {
                Text(
                    stringResource(R.string.dashboard_no_rate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8, Alignment.End),
            ) {
                if (pay == null) {
                    TextButton(onClick = onSetRate) {
                        Text(stringResource(R.string.dashboard_no_rate_action), fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onReview) {
                    Text(stringResource(R.string.dashboard_recap_review), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.dashboard_recap_done),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Spacing.s8),
        )
    }
}
