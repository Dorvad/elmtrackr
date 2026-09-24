package com.elmtrackr.app.data.receipt

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.elmtrackr.app.domain.receipt.OcrLine
import com.elmtrackr.app.domain.receipt.OcrPage
import com.elmtrackr.app.domain.receipt.ReceiptParser
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

    override suspend fun recognize(imagePath: String): Result<OcrPage> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(imagePath)
            require(file.exists() && file.isFile && file.length() > 0) { "Receipt image not found" }
            val decoded = ReceiptBitmapDecoder.decodeDownscaled(imagePath, HEBREW_MAX_DIMENSION_PX)
                ?: error("Unable to read receipt image")
            val variants = runCatching { prepareVariants(decoded) }.getOrNull()
            engineMutex.withLock {
                val tess = obtainEngine()
                try {
                    if (variants == null) {
                        readPage(tess, decoded).also { page ->
                            if (page.text.isBlank()) error("No Hebrew text detected on receipt")
                        }
                    } else {
                        val (grayscale, binary) = variants
                        val fromGray = readPage(tess, grayscale)
                        val fromBinary = readPage(tess, binary)
                        pickReading(fromGray, fromBinary).also { page ->
                            if (page.text.isBlank()) error("No Hebrew text detected on receipt")
                        }
                    }
                } finally {
                    tess.clear()
                    variants?.first?.recycle()
                    variants?.second?.recycle()
                    decoded.recycle()
                }
            }
        }
    }

    /**
     * LSTM keeps letter shapes that a hard threshold destroys; the threshold
     * still wins on a badly lit thermal print. Both run, and the reading that
     * recovered more Hebrew — and a total label, when one of them found one —
     * is the one the parser sees.
     */
    private fun pickReading(first: OcrPage, second: OcrPage): OcrPage =
        if (quality(first) >= quality(second)) first else second

    private fun quality(page: OcrPage): Int {
        val parsed = ReceiptParser().parse(page.text)
        var score = page.text.count { it in 'א'..'ת' } * 3 + page.text.count { it.isDigit() }
        if (parsed.amount != null) score += 10
        if (parsed.amountNearTotalKeyword) score += 50
        return score
    }

    private fun readPage(tess: TessBaseAPI, bitmap: Bitmap): OcrPage {
        tess.setImage(bitmap)
        val text = tess.getUTF8Text().orEmpty().trim()
        val lines = readLines(tess, bitmap.height)
        tess.clear()
        return OcrPage(
            text = text,
            lines = lines.ifEmpty { OcrPage.fromText(text).lines },
        )
    }

    private fun readLines(tess: TessBaseAPI, imageHeight: Int): List<OcrLine> {
        val iterator = tess.resultIterator ?: return emptyList()
        val height = imageHeight.toFloat().coerceAtLeast(1f)
        val lines = mutableListOf<OcrLine>()
        return try {
            iterator.begin()
            do {
                val line = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE).orEmpty().trim()
                val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                if (line.isNotEmpty() && rect != null) {
                    lines.add(OcrLine(line, rect.top / height, rect.bottom / height))
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            lines
        } catch (_: RuntimeException) {
            emptyList()
        } finally {
            iterator.delete()
        }
    }

    private fun obtainEngine(): TessBaseAPI {
        engine?.let { return it }
        val dataDir = ensureTrainedData()
        val tess = TessBaseAPI()
        // Receipt words are abbreviations and prices, not dictionary words. Left
        // on, the Hebrew dawg rewrites a faded סה"כ into the nearest real word
        // and the total label disappears. These have to be set before init.
        tess.setVariable("load_system_dawg", "F")
        tess.setVariable("load_freq_dawg", "F")
        if (!tess.init(dataDir.absolutePath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
            tess.recycle()
            error("Hebrew OCR engine failed to initialize")
        }
        // A receipt is one column of text, already cropped and deskewed by the
        // document scanner before it reaches here. PSM_AUTO spends its effort
        // looking for a page layout that is not there and, on a tall narrow
        // image, regularly decides the column is several blocks and reorders
        // them — which breaks the parser's central assumption that a total label
        // and its amount arrive on the same line.
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        // Without this Tesseract guesses the resolution from the image and says
        // so in its log; the guess is usually wrong for a downscaled photo and
        // it scales its own internal thresholds by it.
        tess.setVariable("user_defined_dpi", ASSUMED_DPI)
        // Keeps the run of spaces between a Hebrew label and its amount, so the
        // two stay one line rather than being collapsed into an ambiguous pair.
        tess.setVariable("preserve_interword_spaces", TessBaseAPI.VAR_TRUE)
        engine = tess
        return tess
    }

    /**
     * Grayscale, adaptively binarized, and upscaled if the photo is small.
     *
     * Tesseract's own preprocessing assumes an evenly lit page; a hand-held
     * photo of a curling thermal receipt is not one. See [ReceiptImageBinarizer].
     *
     * The upscale is separate and matters on its own: Tesseract's models are
     * trained around a capital height of roughly 30 px, and receipt type
     * photographed from a phone at arm's length lands well under that once the
     * image is downscaled for memory. Interpolating first and thresholding
     * afterwards keeps the strokes smooth rather than blocky.
     *
     * Best-effort at the call site: if any of this fails the original bitmap is
     * used, because a worse image still reads better than no OCR at all.
     */
    /**
     * Two images of the same photo: contrast-normalized gray for the LSTM net,
     * and the adaptive black-and-white image for when the gray pass still
     * drowns in a shadow. See [pickReading].
     */
    private fun prepareVariants(source: Bitmap): Pair<Bitmap, Bitmap> {
        val longSide = maxOf(source.width, source.height)
        val scaled = if (longSide in 1 until MIN_OCR_LONG_SIDE_PX) {
            val factor = (MIN_OCR_LONG_SIDE_PX + longSide - 1) / longSide
            source.scale(source.width * factor, source.height * factor)
        } else {
            source
        }
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        val gray = ReceiptImageBinarizer.contrastNormalize(pixels, width, height)
        val binary = ReceiptImageBinarizer.binarize(pixels, width, height)
        return Bitmap.createBitmap(gray, width, height, Bitmap.Config.ARGB_8888) to
            Bitmap.createBitmap(binary, width, height, Bitmap.Config.ARGB_8888)
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

    private companion object {
        const val LANGUAGE = "heb"

        /**
         * The DPI Tesseract is told to assume.
         *
         * 300 is what its models were trained against. The figure is a statement
         * about the *text* size it should expect, not about the file — which is
         * why it is paired with the upscale in [prepare] rather than measured
         * from the image.
         */
        const val ASSUMED_DPI = "300"

        /** Below this, the photo is upscaled before binarizing. See [prepare]. */
        const val MIN_OCR_LONG_SIDE_PX = 2000

        /**
         * Hebrew on a phone photo of a receipt is smaller than the digits ML Kit
         * is happy with. 2048 leaves a line of thermal type under the ~30 px
         * capital height the model was trained at; 3072 is the extra room.
         */
        const val HEBREW_MAX_DIMENSION_PX = 3072

        /** Bump when the bundled traineddata asset changes to force a re-copy. */
        const val TRAINEDDATA_VERSION = 1
    }
}
