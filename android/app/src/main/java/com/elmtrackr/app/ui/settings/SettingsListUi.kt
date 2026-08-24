package com.elmtrackr.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.auroraExpandable
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.ui.text.style.TextAlign
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSurfaceSub

internal val SettingsAvatarGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

@Composable
internal fun SettingsPageHeader() {
    Text(
        stringResource(R.string.settings_title),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsProfileHeroCard(
    displayName: String,
    email: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = {
            AuroraHaptics.navigationTap(haptic)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .auroraPressScale(interactionSource)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(0.06f), spotColor = Color.Black.copy(0.08f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(SettingsAvatarGradient, RoundedCornerShape(percent = 30)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                Text(
                    displayName.ifBlank { stringResource(R.string.settings_your_profile) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).mirrorInRtl(),
            )
        }
    }
}

@Composable
internal fun SettingsGroupedSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.Large),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsHubNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = AuroraIndigo,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .auroraRowClickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(CornerRadius.Small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Spacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).mirrorInRtl(),
            )
        }
        if (showDivider) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
internal fun SettingsDetailHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
internal fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            content()
        }
    }
}

@Composable
internal fun SettingsSectionCardPlain(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            content()
        }
    }
}

@Composable
internal fun SettingsCollapsibleCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Surface(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.auroraExpandable(expanded),
                color = if (expanded) MaterialTheme.colorScheme.surface else auroraSurfaceSub(),
                shape = RoundedCornerShape(
                    topStart = CornerRadius.Large,
                    topEnd = CornerRadius.Large,
                    bottomStart = if (expanded) 0.dp else CornerRadius.Large,
                    bottomEnd = if (expanded) 0.dp else CornerRadius.Large,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedVisibility(
                            visible = !expanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) {
                            stringResource(R.string.settings_collapse, title)
                        } else {
                            stringResource(R.string.settings_expand, title)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .auroraRowClickable(onClick = onClick)
            .background(auroraSurfaceSub(), shape)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp).mirrorInRtl(),
        )
    }
}

@Composable
internal fun SettingsSubsectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = AuroraIndigo,
) {
    val haptic = LocalHapticFeedback.current
    // The whole row is the switch, as one node.
    //
    // With an interactive Switch and the title in a separate Text beside it, a
    // screen reader reaches an unnamed control and announces "switch, off" —
    // correct about the state and silent about which setting it belongs to,
    // which is the one thing the user needed. Merging the row gives the control
    // the title as its name and the row as its target, and matches the pattern
    // already used on the compensation settings screen.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { value ->
                    AuroraHaptics.toggle(haptic)
                    onCheckedChange(value)
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconTint.copy(alpha = if (enabled) 0.14f else 0.06f), RoundedCornerShape(CornerRadius.Small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        // Null handler: the row owns the toggle action, so the switch is the
        // visual state and not a second, separately-announced control.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

/**
 * One option in a single-choice list, as a radio row.
 *
 * The list this belongs to must carry `Modifier.selectableGroup()`, which is what
 * lets a screen reader announce "2 of 4" rather than four unrelated radio buttons.
 *
 * Modelled on [SettingsToggleRow] and merged for the same reason: with an
 * interactive [RadioButton] beside a separate title, a screen reader reaches an
 * unnamed control and says "radio button, not selected" — accurate about the
 * state and silent about which option it is. The row is the target and the title
 * is its name; the radio is state only.
 */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Rendered on the trailing edge, e.g. to mark the option already in effect. */
    trailingLabel: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    AuroraHaptics.toggle(haptic)
                    onSelect()
                },
            )
            // The row is the touch target, and the radio inside it is not
            // interactive, so nothing else reserves a minimum height for it.
            .heightIn(min = Layout.minTouchTarget)
            .padding(vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingLabel != null) {
            Text(
                trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Wraps a set of [SettingsChoiceRow]s so they are announced as one group. */
@Composable
internal fun SettingsChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.selectableGroup(), content = content)
}

@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shared by every settings screen showing a label/value pair.
        //
        // Row measures unweighted children in order, each with the width left over
        // from the previous one, so a long label starves the value of space and
        // SpaceBetween has no slack left to separate them with. Weighting the label
        // reverses the order — the value is measured first and takes what it needs
        // — and the start padding keeps a gap regardless. ProjectInfoRow and
        // ReviewRow already do exactly this; these rows were the outliers.
        //
        // Not reproducible in the JVM test harness: Robolectric measures text at
        // roughly 1dp per character and ignores fontScale, so no assertion here can
        // reach the crowded state. Needs a device at a large display size.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Spacing.s8),
        )
    }
}

@Composable
internal fun SettingsFloatingSaveBar(
    unsavedCount: Int,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
        shape = RoundedCornerShape(CornerRadius.Large),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    if (unsavedCount == 1) R.string.settings_unsaved_change else R.string.settings_unsaved_changes,
                    unsavedCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElmGradientButton(
                onClick = {
                    AuroraHaptics.success(haptic)
                    onSave()
                },
                enabled = !isSaving,
                compact = true,
            ) {
                Text(stringResource(if (isSaving) R.string.settings_saving else R.string.settings_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
