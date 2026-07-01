package com.elmtrackr.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.navigation.BottomNavItem
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraHair
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.AuroraWhite
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.auroraNavSelectedLabel
import com.elmtrackr.app.ui.theme.auroraNavUnselectedIcon
import com.elmtrackr.app.ui.theme.auroraNavUnselectedLabel

private val logoGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

private val SideNavWidth = 220.dp

@Composable
fun ElmSideNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(SideNavWidth)
            .fillMaxHeight()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(0.dp),
                ambientColor = AuroraIndigo.copy(alpha = 0.04f),
                spotColor = AuroraIndigo.copy(alpha = 0.08f),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SideNavLogo(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))

            HorizontalDivider(color = AuroraHair, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BottomNavItem.entries.forEach { item ->
                    SideNavItem(
                        item = item,
                        isSelected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SideNavLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = AuroraIndigo.copy(alpha = 0.15f),
                    spotColor = AuroraIndigo.copy(alpha = 0.7f),
                )
                .background(logoGradient, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = AuroraWhite,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "elmtrackr",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SideNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = if (isSelected) {
        AuroraIndigo.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) auroraNavSelectedLabel() else auroraNavUnselectedLabel()
    val iconColor = if (isSelected) AuroraIndigo else auroraNavUnselectedIcon()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = "${item.label}${if (isSelected) ", selected" else ""}"
            }
            .auroraPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    AuroraHaptics.navigationTap(haptic)
                    onClick()
                },
            )
            .background(backgroundColor, RoundedCornerShape(CornerRadius.Medium))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}
