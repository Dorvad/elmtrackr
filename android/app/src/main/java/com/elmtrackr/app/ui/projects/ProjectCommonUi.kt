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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyFormat
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.ui.theme.CornerRadius
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

@Composable
fun LocalDate.formattedMedium(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(rememberProjectLocale()))

@Composable
fun formattedMinutes(minutes: Int): String = MoneyFormat.formatMinutes(minutes, rememberProjectLocale())

/** Small pill used for work and billing status. */
@Composable
fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(CornerRadius.Small))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
fun WorkStatusPill(status: ProjectWorkStatus, modifier: Modifier = Modifier) {
    StatusPill(workStatusLabel(status), ProjectLabels.workStatusColor(status), modifier)
}

@Composable
fun BillingStatusPill(status: ProjectBillingStatus, modifier: Modifier = Modifier) {
    val accent = when (status) {
        ProjectBillingStatus.OVERDUE -> MaterialTheme.colorScheme.error
        ProjectBillingStatus.PAID -> ProjectPaidGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(billingStatusLabel(status), accent, modifier)
}

/** Label-and-value row, with the value end-aligned so it mirrors in RTL. */
@Composable
fun ProjectInfoRow(
    label: String,
    value: String,
    emphasis: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(bottom = Spacing.xs, start = 2.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.Large),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
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
internal val ProjectPaidGreen = Color(0xFF1E9E63)
