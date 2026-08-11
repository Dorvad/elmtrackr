package com.elmtrackr.app.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.ui.design.ElmIconTile
import com.elmtrackr.app.ui.design.ElmIconTileSize
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraSemantics

/**
 * The chooser behind the add action: work, vacation or sick leave.
 *
 * Clocking in is untouched. That flow is the one people use every day and it
 * stays a single tap; this sheet only replaces what used to happen when someone
 * asked to add an entry *manually*, where one extra decision costs nothing and
 * saves them from recording a day off as an eight-hour shift.
 *
 * Each row carries an icon and a label. Colour alone never says which is which —
 * the icons differ, the titles differ, and the subtitles say in words what each
 * one is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEntryTypeSheet(
    onDismiss: () -> Unit,
    onWork: () -> Unit,
    onAbsence: (AbsenceType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH)
                .padding(bottom = Spacing.s32),
        ) {
            Text(
                text = stringResource(R.string.leave_report_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.s12))

            EntryTypeRow(
                icon = Icons.Outlined.WorkOutline,
                accent = AuroraIndigo,
                title = stringResource(R.string.leave_report_work),
                subtitle = stringResource(R.string.leave_report_work_sub),
                onClick = onWork,
            )
            EntryTypeRow(
                icon = Icons.Outlined.BeachAccess,
                accent = auroraSemantics.info,
                title = stringResource(R.string.leave_report_vacation),
                subtitle = stringResource(R.string.leave_report_vacation_sub),
                onClick = { onAbsence(AbsenceType.VACATION) },
            )
            EntryTypeRow(
                icon = Icons.Outlined.HealthAndSafety,
                accent = auroraSemantics.warning,
                title = stringResource(R.string.leave_report_sick),
                subtitle = stringResource(R.string.leave_report_sick_sub),
                onClick = { onAbsence(AbsenceType.SICK) },
            )
        }
    }
}

@Composable
private fun EntryTypeRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the target, not just the label, and it clears the
            // 48dp minimum whatever the font scale does to the two text lines.
            .heightIn(min = Layout.minTouchTarget)
            .auroraRowClickable(onClick)
            .padding(vertical = Layout.listRowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElmIconTile(background = accent.copy(alpha = ICON_TILE_ALPHA), size = ElmIconTileSize.Medium) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Spacing.s20))
        }
        Spacer(Modifier.width(Layout.inlineGap))
        Column(
            modifier = Modifier
                .padding(horizontal = Layout.inlineGap)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = auroraSecondaryText())
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(Spacing.s20)
                .mirrorInRtl(),
        )
    }
}

private const val ICON_TILE_ALPHA = 0.12f
