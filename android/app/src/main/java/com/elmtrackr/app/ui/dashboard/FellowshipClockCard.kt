package com.elmtrackr.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.components.motion.activeShiftPulse
import kotlinx.coroutines.delay
import java.time.LocalTime

private val FellowshipGold = Color(0xFFD4AF37)
private val FellowshipGoldDark = Color(0xFF8B6914)

private val fellowshipBackgrounds = listOf(
    R.drawable.fellowship_bg_shire,
    R.drawable.fellowship_bg_prancing_pony,
    R.drawable.fellowship_bg_rivendell,
    R.drawable.fellowship_bg_lothlorien,
    R.drawable.fellowship_bg_amon_hen,
    R.drawable.fellowship_bg_khazad_dum,
    R.drawable.fellowship_bg_mount_doom,
)

private val medievalTimerStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    letterSpacing = 1.5.sp,
    color = FellowshipGold,
    shadow = Shadow(
        color = FellowshipGoldDark,
        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
        blurRadius = 4f,
    ),
)

@Composable
fun FellowshipClockCard(
    activeShift: Shift?,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onEditStartTime: () -> Unit,
) {
    var hourTick by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            hourTick = LocalTime.now().hour
        }
    }
    val sceneIndex = hourTick % fellowshipBackgrounds.size

    val idleTransition = rememberInfiniteTransition(label = "fellowship-idle")
    val bob by idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fellowship-bob",
    )
    val characterOffsetY = (bob * 2f).dp
    val characterSway = ((bob - 0.5f) * 1.5f).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(CornerRadius.Large)),
    ) {
        AnimatedContent(
            targetState = sceneIndex,
            transitionSpec = {
                fadeIn(tween(900)) togetherWith fadeOut(tween(700))
            },
            label = "fellowship-scene",
        ) { index ->
            Image(
                painter = painterResource(fellowshipBackgrounds[index]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = characterSway, y = characterOffsetY)
                .fillMaxWidth(0.92f)
                .height(72.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(R.drawable.fellowship_characters),
                contentDescription = "Fellowship companions",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeShift != null) {
                Text(
                    text = formatFellowshipElapsed(elapsedSeconds),
                    style = medievalTimerStyle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        onClick = if (activeShift != null) onClockOut else onClockIn,
                    )
                    .semantics {
                        contentDescription = if (activeShift != null) {
                            "Clock out. End your current shift."
                        } else {
                            "Clock in. Start tracking your shift."
                        }
                    },
            )

            Button(
                onClick = if (activeShift != null) onClockOut else onClockIn,
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = FellowshipGold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .activeShiftPulse(activeShift != null),
            ) {
                Text(
                    text = if (activeShift != null) "Clock Out" else "Begin Quest",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatFellowshipElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
