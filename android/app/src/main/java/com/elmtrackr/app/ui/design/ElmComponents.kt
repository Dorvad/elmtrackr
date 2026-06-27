package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraWhite
import com.elmtrackr.app.ui.theme.auroraOvertimeBackground
import com.elmtrackr.app.ui.theme.auroraOvertimeInk
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraSyncedPillBackground
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import com.elmtrackr.app.ui.theme.auroraWeekendBackground
import com.elmtrackr.app.ui.theme.auroraWeekendInk
import com.elmtrackr.app.ui.sync.SyncStatusUi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun ElmSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text          = title.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = auroraSecondaryText(),
        fontWeight    = FontWeight.Bold,
        letterSpacing = TextUnit(0.16f, TextUnitType.Em),
        modifier      = modifier,
    )
}

// ── Status pill ───────────────────────────────────────────────────────────────

@Composable
fun ElmSyncPill(
    status: SyncStatusUi,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clickableModifier = if (status.isActionable && onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = status.contentDescription }
    } else {
        Modifier.semantics { contentDescription = status.contentDescription }
    }
    Row(
        modifier = modifier
            .then(clickableModifier)
            .shadow(
                elevation = if (status.isActionable) 4.dp else 2.dp,
                shape = RoundedCornerShape(50),
                ambientColor = AuroraIndigo.copy(alpha = 0.08f),
                spotColor = AuroraIndigo.copy(alpha = 0.2f),
            )
            .background(status.background, RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status.showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = status.color,
            )
        }
        Text(
            text = status.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = status.color,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
fun ElmStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    variant: ElmStatVariant = ElmStatVariant.DEFAULT,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    val gradient = Brush.linearGradient(
        colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
    )
    val background = when (variant) {
        ElmStatVariant.DEFAULT -> MaterialTheme.colorScheme.surface
        ElmStatVariant.PRIMARY -> Color.Transparent
        ElmStatVariant.OVERTIME -> auroraOvertimeBackground()
        ElmStatVariant.WEEKEND -> auroraWeekendBackground()
    }
    val labelColor = when (variant) {
        ElmStatVariant.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
        ElmStatVariant.PRIMARY -> AuroraWhite.copy(alpha = 0.8f)
        ElmStatVariant.OVERTIME -> auroraOvertimeInk()
        ElmStatVariant.WEEKEND -> auroraWeekendInk()
    }
    val statColor = when (variant) {
        ElmStatVariant.DEFAULT -> MaterialTheme.colorScheme.onSurface
        ElmStatVariant.PRIMARY -> AuroraWhite
        ElmStatVariant.OVERTIME -> AuroraPeachDeep
        ElmStatVariant.WEEKEND -> auroraWeekendInk()
    }
    val surfaceModifier = modifier
        .shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = AuroraIndigo.copy(alpha = 0.05f),
            spotColor = AuroraIndigo.copy(alpha = if (variant == ElmStatVariant.PRIMARY) 0.55f else 0.34f),
        )
        .then(
            if (variant == ElmStatVariant.PRIMARY) Modifier.background(gradient, shape)
            else Modifier.background(background, shape),
        )
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)

    Box(modifier = surfaceModifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text       = label.uppercase(),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = labelColor,
                maxLines   = 1,
                letterSpacing = TextUnit(0.08f, TextUnitType.Em),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = statColor,
                maxLines   = 1,
            )
            sub?.let {
                Spacer(Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = labelColor)
            }
        }
    }
}

enum class ElmStatVariant { DEFAULT, PRIMARY, OVERTIME, WEEKEND }

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun ElmEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CornerRadius.Large)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = null,
                modifier        = Modifier.size(36.dp),
                tint            = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = subtitle,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Loading state ─────────────────────────────────────────────────────────────

@Composable
fun ElmLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
