package com.elmtrackr.app.data.receipt

import com.elmtrackr.app.domain.receipt.ReceiptTextRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLKitReceiptTextRecognizer @Inject constructor() : ReceiptTextRecognizer {

    /**
     * Built on first use and never allowed to throw.
     *
     * This was a constructor-time field, which made building the ML Kit client part of
     * creating the refund ViewModel's dependency graph: anything that broke the client
     * took down the whole screen the moment the travel-refund section composed — as
     * happened when R8 full mode optimized ML Kit's internal singletons and
     * `getClient` read a null instance. A device without Play Services can fail the
     * same way. The client is only needed once a receipt is actually scanned, and
     * Tesseract still covers Hebrew when Latin OCR is unavailable, so degrade to
     * "couldn't read this receipt" instead of crashing.
     */
    private val recognizer by lazy {
        runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull()
    }

    override suspend fun recognizeText(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = recognizer ?: error("Latin text recognition is unavailable on this device")
            val file = File(imagePath)
            require(file.exists() && file.isFile && file.length() > 0) { "Receipt image not found" }
            val bitmap = ReceiptBitmapDecoder.decodeDownscaled(imagePath)
                ?: error("Unable to read receipt image")
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = client.process(image).await()
                result.text.trim().ifBlank { error("No text detected on receipt") }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
