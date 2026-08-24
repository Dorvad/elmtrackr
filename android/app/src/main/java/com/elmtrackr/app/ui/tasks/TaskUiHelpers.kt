package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.tasks.TaskSorting

val TASK_EMOJI_OPTIONS = listOf("💼", "🛠️", "📋", "💻", "🎨", "📞", "🏠", "🚗", "📚", "⚡", "🧑‍💻", "✍️")

val TASK_COLOR_OPTIONS = listOf(
    "#5B4DF2",
    "#7C3AED",
    "#0EA5E9",
    "#10B981",
    "#F59E0B",
    "#F43F5E",
    "#DB2777",
    "#2563EB",
    "#0D9488",
    "#EA580C",
)

fun Task.resolveColor(): Color = parseTaskColor(color) ?: Color(0xFF5B4DF2)

/**
 * Kept as the name every task call site already uses; the rule itself is shared
 * with work profiles so one malformed-colour behaviour covers both.
 */
fun parseTaskColor(hex: String?): Color? =
    com.elmtrackr.app.ui.common.parseIdentityColor(hex)

fun sortedTasksForDisplay(tasks: List<Task>, suggestedTaskId: String? = null): List<Task> {
    val sorted = TaskSorting.byRecency(tasks)
    val suggested = suggestedTaskId?.let { id -> sorted.firstOrNull { it.id == id } } ?: return sorted
    return listOf(suggested) + sorted.filter { it.id != suggested.id }
}

/**
 * A colour swatch for the task editor.
 *
 * When [onClick] is given the swatch becomes a radio-style option: the 28dp
 * circle stays the visual while the touch target grows to the 48dp minimum, and
 * the selection is announced rather than being conveyed by a white ring alone.
 */
@Composable
fun TaskColorDot(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val swatch = Modifier
        .size(28.dp)
        .clip(CircleShape)
        .background(color)
        .then(
            if (selected) {
                Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            } else {
                Modifier
            },
        )

    if (onClick == null) {
        Box(modifier = modifier.then(swatch))
        return
    }

    Box(
        modifier = modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .minimumInteractiveComponentSize()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(swatch)
    }
}

@Composable
fun TaskChipColorLeading(color: Color?) {
    if (color == null) return
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
