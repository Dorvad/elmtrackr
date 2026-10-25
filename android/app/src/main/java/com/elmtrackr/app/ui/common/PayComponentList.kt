package com.elmtrackr.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.ui.theme.Spacing

/**
 * One currency's pay, split the way the engines split it.
 *
 * The four buckets plus night are exactly what make up `totalGross`: the night uplift
 * is blended into each bracket and then carried out into [night], so
 * `regular + overtime + weekend + holiday + night == total`. Showing all five is what
 * lets the rows add up. The Reports card used to show three cells under a total —
 * with weekend money labelled "Holiday" and night money nowhere — so its parts did
 * not sum to the figure printed above them.
 */
data class PayComponents(
    val currencyCode: String,
    val regular: Double,
    val overtime: Double,
    val weekend: Double,
    val holiday: Double,
    val night: Double,
    val total: Double,
    /** Deductions the user configured on the profile. An estimate, never a tax figure. */
    val deductions: Double = 0.0,
) {
    val afterDeductions: Double get() = maxOf(0.0, total - deductions)

    companion object {
        fun of(totals: PayrollCalculator.CurrencyPayTotals) = PayComponents(
            currencyCode = totals.currencyCode,
            regular = totals.regularGross,
            overtime = totals.overtimeGross,
            weekend = totals.weekendGross,
            holiday = totals.holidayGross,
            night = totals.nightGross,
            total = totals.totalGross,
            deductions = totals.deductionsGross,
        )

        fun of(breakdown: ShiftPayBreakdown) = PayComponents(
            currencyCode = breakdown.currencyCode,
            regular = breakdown.regularGross,
            overtime = breakdown.overtimeGross,
            weekend = breakdown.weekendGross,
            holiday = breakdown.holidayGross,
            night = breakdown.nightGross,
            total = breakdown.totalGross,
            deductions = breakdown.deductionsGross,
        )
    }
}

/**
 * The rows of [components] that hold money, in a fixed order, as label/amount pairs.
 *
 * Zero rows are left out: a month with no overtime does not need an "Overtime 0.00"
 * line to be understood. Regular pay is always shown so a single-row list still says
 * what kind of pay it is.
 */
internal fun PayComponents.visibleRows(): List<Pair<Int, Double>> = buildList {
    add(R.string.pay_component_regular to regular)
    if (overtime > EPSILON) add(R.string.pay_component_overtime to overtime)
    if (weekend > EPSILON) add(R.string.pay_component_weekend to weekend)
    if (holiday > EPSILON) add(R.string.pay_component_holiday to holiday)
    if (night > EPSILON) add(R.string.pay_component_night to night)
}

private const val EPSILON = 0.005

@Composable
fun PayComponentList(
    components: PayComponents,
    modifier: Modifier = Modifier,
    showDeductions: Boolean = true,
    /** Off where the caller already shows the total as its headline. */
    showTotal: Boolean = true,
) {
    val locale = appLocale()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        components.visibleRows().forEach { (label, amount) ->
            PayComponentRow(label, MoneyFormatter.format(amount, components.currencyCode, locale))
        }
        if (showTotal) {
            HorizontalDivider(
                Modifier.padding(vertical = Spacing.s2),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PayComponentRow(
                R.string.pay_component_total,
                MoneyFormatter.format(components.total, components.currencyCode, locale),
                emphasized = true,
            )
        }
        if (showDeductions && components.deductions > EPSILON) {
            PayComponentRow(
                R.string.pay_component_deductions,
                MoneyFormatter.format(-components.deductions, components.currencyCode, locale),
            )
            PayComponentRow(
                R.string.pay_component_after_deductions,
                MoneyFormatter.format(components.afterDeductions, components.currencyCode, locale),
            )
        }
    }
}

@Composable
private fun PayComponentRow(@StringRes label: Int, value: String, emphasized: Boolean = false) {
    val labelText = stringResource(label)
    Row(
        Modifier
            .fillMaxWidth()
            // Read as one item — "Overtime, ₪120.00" — rather than two stops.
            .semantics(mergeDescendants = true) { contentDescription = "$labelText, $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            labelText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Spacing.s8),
        )
    }
}
