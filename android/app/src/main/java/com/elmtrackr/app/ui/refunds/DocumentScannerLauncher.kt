package com.elmtrackr.app.ui.refunds

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun rememberDocumentScannerLauncher(
    onScanStarted: () -> Unit = {},
    onScanFailed: (String?) -> Unit = {},
    onScanResult: (Uri?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val uri = scanResult?.pages?.firstOrNull()?.imageUri
            onScanResult(uri)
        } else {
            onScanResult(null)
        }
    }

    return {
        onScanStarted()
        val hostActivity = activity
        if (hostActivity == null) {
            onScanFailed("Receipt scanner requires an Activity context")
        } else {
            scanner.getStartScanIntent(hostActivity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { error ->
                    onScanFailed(error.message)
                }
        }
    }
}
