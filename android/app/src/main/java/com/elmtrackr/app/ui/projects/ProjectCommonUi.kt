package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.auroraSemantics
import com.elmtrackr.app.ui.theme.auroraStatusPillInk
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyFormat
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The UI locale, so money and dates format the way the user reads them. */
@Composable
fun rememberProjectLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun Money.formatted(): String = MoneyFormat.format(this, rememberProjectLocale())

/**
 * The amount with its ISO code spelled out, for a list that holds more than one
 * currency.
 *
 * CLDR already keeps symbols distinct within a locale — USD is "$" where CAD is
 * "CA$" — so this is not about a symbol collision. It is about a reader
 * scanning a report with two currencies in it, where a bare glyph does not say
 * which row they are looking at. The code and the number are joined by a
 * translatable pattern, so the order is the translator's to choose, and the
 * code is isolated so it cannot be reordered by Hebrew text around it.
 */
@Composable
fun Money.formattedWithCode(): String {
    val locale = rememberProjectLocale()
    if (!MoneyFormat.symbolNeedsCode(this, locale)) return MoneyFormat.format(this, locale)
    return stringResource(
        R.string.project_money_with_code,
        MoneyFormat.formatNumber(this, locale),
        BidiText.isolate(currencyCode),
    )
}

/**
 * Date-only display.
 *
 * A billed, due or payment date is a calendar date, held as an epoch day and
 * never as an instant, so there is no time zone to shift it across and no
 * midnight at which it changes on its own. Only the rendering is locale-aware.
 */
@Composable
fun LocalDate.formattedMedium(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(rememberProjectLocale()))

/** "12h 30m" in English, "12 שע׳ 30 דק׳" in Hebrew — units from resources. */
@Composable
fun formattedMinutes(minutes: Int): String {
    val parts = MoneyFormat.durationParts(minutes, rememberProjectLocale())
    return when {
        parts.hours == 0 -> stringResource(R.string.project_duration_m, parts.minutesText)
        parts.minutes == 0 -> stringResource(R.string.project_duration_h, parts.hoursText)
        else -> stringResource(R.string.project_duration_hm, parts.hoursText, parts.minutesText)
    }
}

/**
 * Small pill used for work and billing status.
 *
 * The tint is decoration. The status name is always written in the pill, so the
 * distinction survives greyscale, colour blindness and a screen reader — none
 * of which can act on "the red one". [spoken] names which kind of status this
 * is, because "Paid" and "Active" mean different things and a pill on its own
 * does not say which axis it sits on.
 */
@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    spoken: String? = null,
) {
    // The label takes an ink, not the accent. The container is a 14% tint of the
    // accent, so using the accent for both measured the text against a wash of
    // itself — 1.83:1 for the Active pill's aqua, 2.59:1 for Paused, 3.56:1 for
    // Completed. The tint keeps the accent; only the text moves.
    val ink = auroraStatusPillInk(accent)
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = spoken ?: text }
            // Fully-rounded pill, per the Aurora reference status chips.
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}

@Composable
fun WorkStatusPill(status: ProjectWorkStatus, modifier: Modifier = Modifier) {
    val label = workStatusLabel(status)
    StatusPill(
        text = label,
        accent = ProjectLabels.workStatusColor(status),
        modifier = modifier,
        spoken = stringResource(R.string.project_a11y_work_status, label),
    )
}

@Composable
fun BillingStatusPill(status: ProjectBillingStatus, modifier: Modifier = Modifier) {
    val accent = when (status) {
        ProjectBillingStatus.OVERDUE -> MaterialTheme.colorScheme.error
        ProjectBillingStatus.PAID -> auroraSemantics.successInk
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = billingStatusLabel(status)
    StatusPill(
        text = label,
        accent = accent,
        modifier = modifier,
        spoken = stringResource(R.string.project_a11y_billing_status, label),
    )
}

/**
 * Label-and-value row, with the value end-aligned so it mirrors in RTL.
 *
 * The two texts are announced as one phrase rather than as two nodes. Swiping
 * through a column of bare amounts — "1,200.00", "216.00", "1,416.00" — with no
 * label attached to any of them is the difference between a readable screen and
 * an unusable one, and the visual pairing that makes it obvious on screen
 * carries no meaning to a screen reader.
 *
 * The label is allowed to take the space it needs and to wrap; the value keeps
 * its own width. A long translated label under a large font scale then grows
 * downwards instead of crushing the number next to it.
 */
@Composable
fun ProjectInfoRow(
    label: String,
    value: String,
    emphasis: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    /** Overrides what a screen reader announces, when the visible text is terse. */
    a11yValue: String? = null,
) {
    val spoken = stringResource(R.string.project_a11y_row, label, a11yValue ?: value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

/**
 * The three amounts, always in the same order and always adding up.
 *
 * With tax off, only the single agreed amount is shown — there is no point
 * repeating the same number three times under different names.
 */
@Composable
fun ProjectFeeBreakdown(
    fee: ProjectFee,
    modifier: Modifier = Modifier,
) {
    val locale = rememberProjectLocale()
    Column(modifier = modifier.fillMaxWidth()) {
        if (!fee.taxMode.isEnabled) {
            ProjectInfoRow(
                label = stringResource(R.string.project_amount_client_total),
                value = MoneyFormat.format(fee.clientTotal, locale),
                emphasis = true,
            )
            return@Column
        }
        ProjectInfoRow(
            label = stringResource(R.string.project_amount_base),
            value = MoneyFormat.format(fee.base, locale),
        )
        ProjectInfoRow(
            label = fee.taxLabel?.let { label ->
                stringResource(
                    R.string.project_amount_tax_named,
                    label,
                    MoneyFormat.formatTaxRate(fee.taxRatePercent, locale),
                )
            } ?: stringResource(R.string.project_amount_tax),
            value = MoneyFormat.format(fee.tax, locale),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        ProjectInfoRow(
            label = stringResource(R.string.project_amount_client_total),
            value = MoneyFormat.format(fee.clientTotal, locale),
            emphasis = true,
        )
    }
}

/** Section container matching the settings detail screens. */
@Composable
fun ProjectSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs, start = 4.dp),
        )
        ElmCard {
            Column(Modifier.padding(Spacing.md)) { content() }
        }
    }
}

/** Alias so callers do not need to import ColumnScope explicitly. */
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun ProjectNoteText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun SpacerMd() = Spacer(Modifier.height(Spacing.md))

/** Settled-money green, matching the insights tint used in Settings. */
