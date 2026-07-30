package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.domain.money.MoneyByCurrency

/**
 * Renders a [MoneyByCurrency] as one row per currency.
 *
 * With a single currency this looks like an ordinary labelled amount. With two or
 * more it becomes one row per currency, each labelled with its code — never a
 * single figure, because the app has no exchange rates and one total would be
 * fiction.
 *
 * An empty total renders nothing, so a report does not show "0.00" for a currency
 * that has no activity.
 */
@Composable
fun MoneyByCurrencyRows(
    label: String,
    amounts: MoneyByCurrency,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (amounts.isEmpty) return
    Column(modifier = modifier.fillMaxWidth()) {
        val single = amounts.singleOrNull()
        if (single != null) {
            ProjectInfoRow(
                label = label,
                value = single.formatted(),
                emphasis = emphasis,
                valueColor = valueColor,
            )
            return@Column
        }
        // Two or more: the currency code goes in the label so each row stands
        // alone and nothing implies they can be added.
        amounts.entries().forEach { amount ->
            ProjectInfoRow(
                label = "$label · ${amount.currencyCode}",
                value = amount.formatted(),
                emphasis = emphasis,
                valueColor = valueColor,
            )
        }
    }
}
