package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.common.appLocale

/**
 * The regular / overtime / weekend hours split.
 *
 * This existed twice — once on the dashboard and once in Reports — drawn from
 * the same numbers but with different bar heights (12dp vs 10dp), different
 * corner treatments and two different legend layouts, so the same month looked
 * like two different charts depending on which tab you were on.
 *
 * The Reports rendering is the one that survived, moved here unchanged: it was
 * the more informative of the two (it shows percentages alongside the hours).
 */
@Composable
fun ElmDistributionBar(
    regularMinutes: Int,
    overtimeMinutes: Int,
    weekendMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val total = (regularMinutes + overtimeMinutes + weekendMinutes).coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Segment(regularMinutes, total, AuroraIndigo)
        Segment(overtimeMinutes, total, AuroraPeach)
        Segment(weekendMinutes, total, AuroraPlum)
    }
}

@Composable
private fun RowScope.Segment(
    minutes: Int,
    total: Int,
    color: Color,
) {
    if (minutes <= 0) return
    Box(
        Modifier
            .weight(minutes.toFloat() / total)
            .height(10.dp)
            .background(color, RoundedCornerShape(6.dp)),
    )
}

@Composable
fun ElmDistributionLegend(
    regularMinutes: Int,
    overtimeMinutes: Int,
    weekendMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val total = (regularMinutes + overtimeMinutes + weekendMinutes).coerceAtLeast(1)
    val items = listOf(
        Triple(stringResource(R.string.dashboard_stat_regular), regularMinutes, AuroraIndigo),
        Triple(stringResource(R.string.dashboard_stat_overtime), overtimeMinutes, AuroraPeach),
        Triple(stringResource(R.string.dashboard_stat_weekend), weekendMinutes, AuroraPlum),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        items.forEach { (label, minutes, color) ->
            SegmentLegendRow(
                label = label,
                minutes = minutes,
                color = color,
                percent = ((minutes.toFloat() / total) * 100).toInt(),
            )
        }
    }
}

@Composable
private fun SegmentLegendRow(label: String, minutes: Int, color: Color, percent: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Spacing.s8).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(Spacing.s8))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.s6))
            Text("$percent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.reports_hours_value, HoursFormatter.decimal(minutes, appLocale())),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
