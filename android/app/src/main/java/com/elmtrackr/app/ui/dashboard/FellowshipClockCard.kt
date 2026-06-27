package com.elmtrackr.app.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.components.motion.activeShiftPulse
import com.elmtrackr.app.ui.theme.CornerRadius
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class FellowshipScene(
    val label: String,
    val sky: Color,
    val ground: Color,
    val partyColors: List<Color>,
)

private val FELLOWSHIP_SCENES = listOf(
    FellowshipScene("The Shire", Color(0xFF7EC8E3), Color(0xFF5B8F3E), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Bree", Color(0xFF9BD4F0), Color(0xFFC4A574), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Rivendell", Color(0xFFB8D4E8), Color(0xFF5A8A72), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Moria", Color(0xFF0A0812), Color(0xFF1A1520), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Anduin", Color(0xFF87CEEB), Color(0xFF4A8C3F), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Lothlórien", Color(0xFFC8E6C0), Color(0xFF3D6B2E), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
    FellowshipScene("Mordor", Color(0xFF1A0A10), Color(0xFF2A1510), listOf(Color(0xFF3D5C3A), Color(0xFF9CA3AF), Color(0xFF2D6B3A), Color(0xFF8B2E2E), Color(0xFF8B5A2B), Color(0xFF4A7A34), Color(0xFF5C3D2E), Color(0xFF4A7A34), Color(0xFF2E7A6A))),
)

private fun fellowshipIndex(hour: Int): Int {
    val count = FELLOWSHIP_SCENES.size
    return ((hour % count) + count) % count
}

@Composable
fun FellowshipClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = LocalTime.now().hour
            delay(1000L)
        }
    }
    val scene = FELLOWSHIP_SCENES[fellowshipIndex(hour)]
    val running = activeShift != null
    val bob = rememberInfiniteTransition(label = "fellowship-bob")
    val bobOffset by bob.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "fellowship-bob-offset",
    )
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Large))
            .background(Color(0xFF0C0A18)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(scene.sky, scene.ground))),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xBB080614), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    scene.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (running) formatElapsedTime(elapsedSeconds) else LocalTime.now().format(timeFormatter),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (running) {
                    Text(
                        "since ${formatInstantTime(activeShift!!.startTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .offset(y = (-4 * bobOffset).dp)
                    .fillMaxWidth(0.88f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                scene.partyColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(if (color == scene.partyColors[1] || color == scene.partyColors[4]) 52.dp else 44.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (running) {
                Button(
                    onClick = onEditStartTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                ) { Text("Edit start time") }
            }
            Button(
                onClick = if (running) onClockOut else onClockIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .activeShiftPulse(running)
                    .semantics {
                        contentDescription = if (running) {
                            "Clock out. End your current shift."
                        } else {
                            "Clock in. Start tracking your shift."
                        }
                    },
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = if (running) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                },
            ) {
                Text(if (running) "Clock Out" else "Clock In", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

private fun formatInstantTime(instant: Instant): String =
    instant.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
