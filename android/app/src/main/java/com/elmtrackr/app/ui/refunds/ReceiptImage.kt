package com.elmtrackr.app.ui.refunds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.elmtrackr.app.data.receipt.ReceiptBitmapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a receipt preview is in its life. A form draws something different for each. */
internal sealed interface ReceiptImageState {
    /** No file to show. */
    data object None : ReceiptImageState

    /** A file was given and is being read. */
    data object Loading : ReceiptImageState

    data class Ready(val image: ImageBitmap) : ReceiptImageState

    /** The file is missing, empty or not an image. */
    data object Unreadable : ReceiptImageState
}

/**
 * A receipt photo for an inline preview, decoded off the main thread and to the
 * size a preview needs.
 *
 * The two forms that show one used to call `BitmapFactory.decodeFile(path)` inside
 * `remember`, which is to say during composition, on the main thread, at the
 * file's full resolution. A photo straight from the camera is 12 megapixels or
 * more: tens of megabytes once decoded, and long enough to read that the dialog
 * stalled while it opened. `ReceiptBitmapDecoder` already existed to bound exactly
 * this for OCR and for the PDF export; the previews had been left out.
 *
 * [Loading] is its own state so a caller does not have to guess whether "no image
 * yet" means "none" — the claim form shows a "no receipt attached" placeholder
 * for the latter, and flashing it while a receipt loads would say the opposite of
 * the truth.
 */
@Composable
internal fun rememberReceiptImage(
    path: String?,
    maxDimensionPx: Int = ReceiptBitmapDecoder.PREVIEW_MAX_DIMENSION_PX,
): State<ReceiptImageState> = produceState<ReceiptImageState>(
    initialValue = if (path == null) ReceiptImageState.None else ReceiptImageState.Loading,
    path,
    maxDimensionPx,
) {
    if (path == null) {
        value = ReceiptImageState.None
        return@produceState
    }
    value = ReceiptImageState.Loading
    val image = withContext(Dispatchers.IO) {
        runCatching { ReceiptBitmapDecoder.decodeDownscaled(path, maxDimensionPx)?.asImageBitmap() }
            .getOrNull()
    }
    value = image?.let(ReceiptImageState::Ready) ?: ReceiptImageState.Unreadable
}
