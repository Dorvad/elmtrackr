package com.elmtrackr.app.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.elmtrackr.app.domain.receipt.ReceiptTextRecognizer
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Hebrew OCR via Tesseract. ML Kit's text recognizer has no Hebrew
 * model, so without this engine the labels that mark the receipt total
 * (סה"כ, לתשלום…) are invisible to the parser. The fast Hebrew traineddata
 * (~1 MB) ships in assets and is copied to app storage on first use.
 */
@Singleton
class TesseractHebrewTextRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReceiptTextRecognizer {

    private val engineMutex = Mutex()
    private var engine: TessBaseAPI? = null

    override suspend fun recognizeText(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(imagePath)
            require(file.exists() && file.isFile && file.length() > 0) { "Receipt image not found" }
            val bitmap = decodeDownscaled(imagePath)
                ?: error("Unable to read receipt image")
            engineMutex.withLock {
                val tess = obtainEngine()
                try {
                    tess.setImage(bitmap)
                    tess.getUTF8Text().orEmpty().trim().ifBlank { error("No Hebrew text detected on receipt") }
                } finally {
                    tess.clear()
                    bitmap.recycle()
                }
            }
        }
    }

    private fun obtainEngine(): TessBaseAPI {
        engine?.let { return it }
        val dataDir = ensureTrainedData()
        val tess = TessBaseAPI()
        if (!tess.init(dataDir.absolutePath, LANGUAGE)) {
            tess.recycle()
            error("Hebrew OCR engine failed to initialize")
        }
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        engine = tess
        return tess
    }

    /** Copies assets/tessdata/heb.traineddata into filesDir; Tesseract requires a real file path. */
    private fun ensureTrainedData(): File {
        val baseDir = File(context.filesDir, "ocr")
        val tessDataDir = File(baseDir, "tessdata")
        val target = File(tessDataDir, "$LANGUAGE.traineddata")
        val marker = File(tessDataDir, "$LANGUAGE.traineddata.v$TRAINEDDATA_VERSION")
        if (!target.exists() || !marker.exists()) {
            tessDataDir.mkdirs()
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            tessDataDir.listFiles { f -> f.name.startsWith("$LANGUAGE.traineddata.v") }
                ?.forEach { it.delete() }
            marker.createNewFile()
        }
        return baseDir
    }

    private fun decodeDownscaled(imagePath: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(imagePath, options)
    }

    private companion object {
        const val LANGUAGE = "heb"
        const val MAX_DIMENSION_PX = 2048

        /** Bump when the bundled traineddata asset changes to force a re-copy. */
        const val TRAINEDDATA_VERSION = 1
    }
}
