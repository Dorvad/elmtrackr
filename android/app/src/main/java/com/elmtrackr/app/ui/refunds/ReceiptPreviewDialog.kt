package com.elmtrackr.app.ui.refunds

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ReceiptPreviewDialog(
    receiptUrl: String,
    onDismiss: () -> Unit,
) {
    var bitmap by remember(receiptUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(receiptUrl) { mutableStateOf(false) }

    LaunchedEffect(receiptUrl) {
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                var connection = URL(receiptUrl).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ElmTrackr-Android/1.0")
                }
                // Manually follow redirects across HTTP→HTTPS
                var redirects = 0
                while (connection.responseCode in 300..399 && redirects < 5) {
                    val location = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    connection = URL(location).openConnection() as HttpURLConnection
                    connection.apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "ElmTrackr-Android/1.0")
                    }
                    redirects++
                }
                connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
        failed = bitmap == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 560.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null -> {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Image(
                                bitmap = requireNotNull(bitmap),
                                contentDescription = "Receipt image",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    failed -> {
                        Text(
                            "Receipt preview could not be loaded. Try again in a moment.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    else -> CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
