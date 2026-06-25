package com.elmtrackr.app.data.receipts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.util.UUID

class PhotoFileManager(
    private val rootDir: File,
    private val uriForFile: (File) -> Uri,
    private val clock: Clock = Clock.systemUTC(),
) {
    constructor(context: Context) : this(
        rootDir = File(context.filesDir, RECEIPT_ROOT_DIR),
        uriForFile = { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        },
    )

    fun createPendingPhotoFile(shiftId: String, direction: RefundDirection): File {
        val dir = pendingDir().apply { mkdirs() }
        val safeShift = shiftId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "${safeShift}_${direction.name.lowercase()}_${UUID.randomUUID()}.jpg")
    }

    fun contentUri(file: File): Uri = uriForFile(file)

    suspend fun copyUriToPendingPhoto(
        context: Context,
        uri: Uri,
        shiftId: String,
        direction: RefundDirection,
    ): File? = withContext(Dispatchers.IO) {
        val target = createPendingPhotoFile(shiftId, direction)
        var total = 0
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RECEIPT_BYTES) {
                            output.close()
                            target.delete()
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext null
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    suspend fun toReceiptUpload(path: String): ReceiptUpload? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.isFile || file.length() <= 0 || file.length() > MAX_RECEIPT_BYTES) {
            return@withContext null
        }
        ReceiptUpload(
            fileName = file.name,
            bytes = file.readBytes(),
            mimeType = "image/jpeg",
        )
    }

    fun displayNameFor(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: "receipt.jpg"

    fun delete(path: String?) {
        if (path == null) return
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(rootDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    fun cleanupOrphans(olderThanMillis: Long = DEFAULT_ORPHAN_AGE_MILLIS): Int {
        val dir = pendingDir()
        if (!dir.exists()) return 0
        val cutoff = clock.millis() - olderThanMillis
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .count { file -> runCatching { file.delete() }.getOrDefault(false) }
    }

    private fun pendingDir(): File = File(rootDir, PENDING_DIR)

    companion object {
        const val RECEIPT_ROOT_DIR = "refund_receipts"
        const val PENDING_DIR = "pending"
        const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024
        const val DEFAULT_ORPHAN_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
