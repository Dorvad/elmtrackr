package com.elmtrackr.app.data.receipt

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.elmtrackr.app.domain.model.RefundDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What [ReceiptImageStore.importReceipt] did with a picked photo or PDF. */
sealed interface ReceiptImport {
    data class Saved(val file: File) : ReceiptImport
    data object TooLarge : ReceiptImport
    data object Unreadable : ReceiptImport
}

/**
 * Persists scanned receipt images under app-private storage for local-only retention.
 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val rootDir: File
        get() = File(context.filesDir, RECEIPT_ROOT_DIR).apply { mkdirs() }

    /**
     * Copies a picked receipt into app storage.
     *
     * A PDF is rendered to a JPEG first. The cloud bucket only accepts images,
     * and the OCR engines decode a bitmap, so the stored file is the same kind
     * a camera capture already produces.
     */
    suspend fun importReceipt(
        sourceUri: Uri,
        shiftId: String,
        direction: RefundDirection,
    ): ReceiptImport = withContext(Dispatchers.IO) {
        if (isPdf(sourceUri)) importPdf(sourceUri, shiftId, direction) else importImage(sourceUri, shiftId, direction)
    }

    suspend fun copyToLocalStorage(
        sourceUri: Uri,
        shiftId: String,
        direction: RefundDirection,
    ): File? = when (val imported = importReceipt(sourceUri, shiftId, direction)) {
        is ReceiptImport.Saved -> imported.file
        ReceiptImport.TooLarge, ReceiptImport.Unreadable -> null
    }

    private fun importImage(sourceUri: Uri, shiftId: String, direction: RefundDirection): ReceiptImport {
        val target = jpegTarget(shiftId, direction)
        var total = 0
        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RECEIPT_BYTES) {
                            target.delete()
                            return ReceiptImport.TooLarge
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return ReceiptImport.Unreadable
            ReceiptImport.Saved(target)
        }.getOrElse {
            target.delete()
            ReceiptImport.Unreadable
        }
    }

    private fun importPdf(sourceUri: Uri, shiftId: String, direction: RefundDirection): ReceiptImport {
        val temp = File(rootDir, "pdf_${UUID.randomUUID()}.pdf")
        val target = jpegTarget(shiftId, direction)
        val copied = copyBounded(sourceUri, temp)
        if (copied !is ReceiptImport.Saved) {
            temp.delete()
            return copied
        }
        val rendered = runCatching { ReceiptPdfRasterizer.renderToJpeg(temp, target) }.getOrDefault(false)
        temp.delete()
        return if (rendered && target.length() > 0L) {
            ReceiptImport.Saved(target)
        } else {
            target.delete()
            ReceiptImport.Unreadable
        }
    }

    private fun copyBounded(sourceUri: Uri, target: File): ReceiptImport {
        var total = 0
        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RECEIPT_BYTES) {
                            target.delete()
                            return ReceiptImport.TooLarge
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return ReceiptImport.Unreadable
            ReceiptImport.Saved(target)
        }.getOrElse {
            target.delete()
            ReceiptImport.Unreadable
        }
    }

    private fun isPdf(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime == "application/pdf") return true
        val name = displayName(uri)?.lowercase().orEmpty()
        return name.endsWith(".pdf") || uri.lastPathSegment?.lowercase()?.endsWith(".pdf") == true
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun jpegTarget(shiftId: String, direction: RefundDirection): File {
        val safeShift = shiftId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(rootDir, "${safeShift}_${direction.name.lowercase()}_${UUID.randomUUID()}.jpg")
    }

    /** Returns true when the image no longer exists on disk afterwards. */
    fun delete(path: String?): Boolean {
        if (path == null) return true
        return runCatching {
            val file = File(path)
            when {
                !file.exists() -> true
                !file.canonicalPath.startsWith(rootDir.canonicalPath + File.separator) -> false
                else -> file.delete()
            }
        }.getOrDefault(false)
    }

    companion object {
        const val RECEIPT_ROOT_DIR = "local_receipts"
        const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024
    }
}
