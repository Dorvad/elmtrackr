package com.elmtrackr.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.R

@Composable
fun SetupScreen(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_body),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Button(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

@Composable
fun IdleScreen(
    systemTime: String,
    lastPunchLabel: String,
    todayShort: String,
    onPunchIn: () -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = systemTime, style = MaterialTheme.typography.labelSmall)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.idle_last_punch),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = lastPunchLabel.ifBlank { "--:--" },
                style = MaterialTheme.typography.displaySmall,
            )
            if (todayShort.isNotBlank()) {
                Text(
                    text = "Today $todayShort",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Button(onClick = onPunchIn, enabled = !isLoading) {
            Text(stringResource(R.string.punch_in))
        }
    }
}

@Composable
fun RunningScreen(
    elapsed: String,
    sinceLabel: String,
    progressPercent: Int,
    onPunchOut: () -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = elapsed, style = MaterialTheme.typography.displayLarge)
        Text(
            text = stringResource(R.string.running_since, sinceLabel),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "$progressPercent% of day",
            style = MaterialTheme.typography.labelSmall,
        )
        Button(onClick = onPunchOut, enabled = !isLoading) {
            Text(stringResource(R.string.punch_out))
        }
    }
}

@Composable
fun ConfirmationOverlay(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}
