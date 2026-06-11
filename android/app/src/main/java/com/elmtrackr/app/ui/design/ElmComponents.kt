package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2
import com.elmtrackr.app.ui.theme.AuroraPeach
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
        color         = AuroraInk2,
        fontWeight    = FontWeight.Bold,
        letterSpacing = TextUnit(0.12f, TextUnitType.Em),
        modifier      = modifier.fillMaxWidth(),
    )
}

// ── Status pill ───────────────────────────────────────────────────────────────

@Composable
fun ElmSyncPill(
    pendingCount: Int,
    isRemoteConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, color, bg) = when {
        !isRemoteConfigured -> Triple("Offline mode", AuroraInk2, Color.Transparent)
        pendingCount > 0    -> Triple("↑ $pendingCount pending", AuroraPeach, AuroraPeach.copy(alpha = 0.12f))
        else                -> Triple("✓ Synced", AuroraIndigo, AuroraIndigo.copy(alpha = 0.10f))
    }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
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
    valueColor: Color = AuroraIndigo,
) {
    ElmCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = valueColor,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall,
                color     = AuroraInk2,
                textAlign = TextAlign.Center,
                maxLines  = 1,
            )
        }
    }
}

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
                .background(AuroraIndigo.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = null,
                modifier        = Modifier.size(36.dp),
                tint            = AuroraIndigo.copy(alpha = 0.6f),
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
            color     = AuroraInk2,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Loading state ─────────────────────────────────────────────────────────────

@Composable
fun ElmLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AuroraIndigo)
    }
}
