package com.elmtrackr.app.ui.refunds

import androidx.activity.compose.BackHandler
import android.Manifest
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.data.receipt.ReceiptBitmapDecoder
import java.util.concurrent.Executors
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import kotlinx.coroutines.delay
import java.io.File

/**
 * The captured thumbnail is shown in a small confirmation preview, so it never
 * needs the sensor's full resolution — the saved file keeps that.
 */
private const val THUMBNAIL_MAX_DIMENSION_PX = 512

@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(
    outputFile: File,
    onPhotoCaptured: (String) -> Unit,
    onCaptureFailed: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var showFlash by remember { mutableStateOf(false) }
    var capturedThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // Single-threaded: captures are serialised anyway, and one thread keeps the
    // decode off both the UI thread and CameraX's own callback thread.
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    val motionEnabled = auroraMotionEnabled()

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 0.92f else 0f,
        animationSpec = tween(if (motionEnabled) 120 else 0, easing = AuroraEaseOut),
        label = "camera-shutter-flash",
    )
    val thumbnailVisible = capturedThumbnail != null
    val thumbnailOffset by animateFloatAsState(
        targetValue = if (thumbnailVisible) 0f else 72f,
        animationSpec = tween(if (motionEnabled) 320 else 0, easing = AuroraEaseOut),
        label = "camera-thumbnail-slide",
    )
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (thumbnailVisible) 1f else 0f,
        animationSpec = tween(if (motionEnabled) 220 else 0, easing = AuroraEaseOut),
        label = "camera-thumbnail-alpha",
    )

    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(if (motionEnabled) 140L else 0L)
            showFlash = false
        }
    }

    LaunchedEffect(capturedThumbnail) {
        val bitmap = capturedThumbnail ?: return@LaunchedEffect
        try {
            delay(if (motionEnabled) 420L else 0L)
            onPhotoCaptured(outputFile.absolutePath)
        } finally {
            // Clear the state first, then recycle. The other order left a window
            // in which a draw pass could still reach the Image() below and hit
            // "Canvas: trying to use a recycled bitmap". The finally block also
            // means leaving composition during the delay releases the bitmap
            // instead of leaking it.
            capturedThumbnail = null
            bitmap.recycle()
        }
    }

    BackHandler(onBack = onClose)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!hasPermission) {
            CameraPermissionGate(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )
            return@Box
        }

        val cameraController = remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
        }
        DisposableEffect(lifecycleOwner, cameraController) {
            cameraController.bindToLifecycle(lifecycleOwner)
            onDispose { cameraController.unbind() }
        }

        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    controller = cameraController
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha)),
        )

        capturedThumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.refunds_captured_receipt_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 96.dp)
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = thumbnailAlpha
                        translationY = thumbnailOffset
                    }
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.refunds_receipt_camera),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.refunds_close_camera))
            }
        }

        Button(
            onClick = {
                if (capturedThumbnail != null) return@Button
                AuroraHaptics.shutter(haptic)
                showFlash = true
                outputFile.parentFile?.mkdirs()
                val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                cameraController.takePicture(
                    options,
                    // A background executor, not the main one: decoding the
                    // captured JPEG is the work below, and on a high-megapixel
                    // sensor that is hundreds of milliseconds even when it fits
                    // in memory.
                    captureExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            // Bounded decode. An unbounded decodeFile here was an
                            // OutOfMemoryError waiting for a 50 MP camera — and
                            // because runCatching swallows OOM, the symptom was a
                            // silently missing thumbnail rather than a crash
                            // report. ReceiptBitmapDecoder already existed for
                            // exactly this; the OCR paths used it, this one did not.
                            val decoded = runCatching {
                                ReceiptBitmapDecoder.decodeDownscaled(
                                    outputFile.absolutePath,
                                    maxDimensionPx = THUMBNAIL_MAX_DIMENSION_PX,
                                )
                            }.getOrNull()
                            mainHandler.post { capturedThumbnail = decoded }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onCaptureFailed(exception.message)
                        }
                    },
                )
            },
            enabled = capturedThumbnail == null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            shape = RoundedCornerShape(CornerRadius.Large),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.refunds_capture_receipt))
        }
    }
}

@Composable
private fun CameraPermissionGate(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.refunds_camera_permission_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.refunds_camera_permission_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.refunds_allow_camera))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.refunds_not_now))
        }
    }
}
