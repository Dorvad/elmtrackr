package com.elmtrackr.app.data.receipt

import android.graphics.BitmapFactory
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

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(imagePath)
            require(file.exists() && file.isFile && file.length() > 0) { "Receipt image not found" }
            val bitmap = BitmapFactory.decodeFile(imagePath)
                ?: error("Unable to read receipt image")
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            result.text.trim().ifBlank { error("No text detected on receipt") }
        }
    }
}
