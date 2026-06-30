package com.elmtrackr.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

fun parseTaskColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val normalized = hex.trim().removePrefix("#")
    if (normalized.length != 6) return null
    return runCatching {
        Color(0xFF000000 or normalized.toLong(16))
    }.getOrNull()
}

fun sortedTasksForDisplay(tasks: List<Task>, suggestedTaskId: String? = null): List<Task> {
    val sorted = TaskSorting.byRecency(tasks)
    val suggested = suggestedTaskId?.let { id -> sorted.firstOrNull { it.id == id } } ?: return sorted
    return listOf(suggested) + sorted.filter { it.id != suggested.id }
}

@Composable
fun TaskColorDot(color: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                } else {
                    Modifier
                },
            ),
    )
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
