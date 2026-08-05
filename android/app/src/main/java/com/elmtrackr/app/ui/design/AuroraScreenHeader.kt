package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSurfaceSub

/**
 * The Aurora sub-screen header: a bordered back chip, a display-type title with
 * an optional one-line subtitle, and an optional trailing action — usually a
 * compact [ElmGradientButton]. One pattern for every utility screen instead of
 * each of them improvising an IconButton + titleLarge row.
 */
@Composable
fun AuroraScreenHeader(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.s8, bottom = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chipShape = RoundedCornerShape(CornerRadius.Small)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .background(auroraSurfaceSub(), chipShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, chipShape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = backContentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.s12),
        ) {
            // Shrink-to-fit: display type at 24sp doesn't leave room for a long
            // title between the back chip and a header action on narrow phones,
            // so the size steps down (to a floor) before ellipsizing.
            val baseStyle = MaterialTheme.typography.headlineMedium
            var titleStyle by remember(title, baseStyle) { mutableStateOf(baseStyle) }
            var titleFits by remember(title, baseStyle) { mutableStateOf(false) }
            Text(
                title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && titleStyle.fontSize > MIN_TITLE_SIZE) {
                        titleStyle = titleStyle.copy(fontSize = titleStyle.fontSize * 0.92f)
                    } else {
                        titleFits = true
                    }
                },
                modifier = Modifier
                    .auroraHeading()
                    .drawWithContent { if (titleFits) drawContent() },
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

private val MIN_TITLE_SIZE = 18.sp

/**
 * The rounded-square icon container the redesigned screens lead their rows
 * with. Sizes carry their own corner radius so a tile always keeps the same
 * proportions: list rows use [ElmIconTileSize.Small], task rows
 * [ElmIconTileSize.Medium], and hero/feature tiles [ElmIconTileSize.Large].
 */
enum class ElmIconTileSize(internal val box: Dp, internal val radius: Dp) {
    Small(34.dp, CornerRadius.Chip),
    Medium(42.dp, CornerRadius.Small),
    Large(50.dp, CornerRadius.Medium),
}

@Composable
fun ElmIconTile(
    background: Color,
    modifier: Modifier = Modifier,
    size: ElmIconTileSize = ElmIconTileSize.Medium,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size.box)
            .background(background, RoundedCornerShape(size.radius)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
