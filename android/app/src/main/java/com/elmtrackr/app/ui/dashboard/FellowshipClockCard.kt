package com.elmtrackr.app.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.components.motion.activeShiftPulse
import com.elmtrackr.app.ui.theme.CornerRadius
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val FellowshipGold = Color(0xFFE8C872)
private val FellowshipGoldDeep = Color(0xFFB8860B)
private val FellowshipFontFamily = FontFamily(Font(R.font.cinzel, FontWeight.Bold))

private data class FellowshipScene(
    val label: String,
    val backgroundRes: Int,
)

private val FELLOWSHIP_SCENES = listOf(
    FellowshipScene("The Shire", R.drawable.fellowship_bg_shire),
    FellowshipScene("Bree", R.drawable.fellowship_bg_bree),
    FellowshipScene("Rivendell", R.drawable.fellowship_bg_rivendell),
    FellowshipScene("Moria", R.drawable.fellowship_bg_moria),
    FellowshipScene("Anduin", R.drawable.fellowship_bg_anduin),
    FellowshipScene("Lothlórien", R.drawable.fellowship_bg_lothlorien),
    FellowshipScene("Mordor", R.drawable.fellowship_bg_mordor),
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
    val sceneIndex = fellowshipIndex(hour)
    val scene = FELLOWSHIP_SCENES[sceneIndex]
    val running = activeShift != null
    val bob = rememberInfiniteTransition(label = "fellowship-bob")
    val bobOffset by bob.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "fellowship-bob-offset",
    )
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val stopwatchText = if (running) {
        formatElapsedTime(elapsedSeconds)
    } else {
        LocalTime.now().format(timeFormatter)
    }

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
            Crossfade(
                targetState = sceneIndex,
                animationSpec = tween(1400),
                label = "fellowship-bg",
            ) { index ->
                Image(
                    painter = painterResource(FELLOWSHIP_SCENES[index].backgroundRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xA6080614), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    scene.label.uppercase(),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stopwatchText,
                    fontFamily = FellowshipFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (running && elapsedSeconds >= 3600) 26.sp else 30.sp,
                    color = FellowshipGold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = FellowshipGoldDeep,
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 6f,
                        ),
                    ),
                )
                if (running) {
                    Text(
                        "since ${formatInstantTime(activeShift!!.startTime)}",
                        fontFamily = FellowshipFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = FellowshipGold.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Image(
                painter = painterResource(R.drawable.fellowship_party),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.9f)
                    .offset(y = (-4 * bobOffset).dp),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.BottomCenter,
            )
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
