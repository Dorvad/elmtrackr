package com.elmtrackr.app.data.receipt

import android.content.Context
import android.net.Uri
import com.elmtrackr.app.domain.model.RefundDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists scanned receipt images under app-private storage for local-only retention.
 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val rootDir: File
        get() = File(context.filesDir, RECEIPT_ROOT_DIR).apply { mkdirs() }

    suspend fun copyToLocalStorage(
        sourceUri: Uri,
        shiftId: String,
        direction: RefundDirection,
    ): File? = withContext(Dispatchers.IO) {
        val safeShift = shiftId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val target = File(
            rootDir,
            "${safeShift}_${direction.name.lowercase()}_${UUID.randomUUID()}.jpg",
        )
        var total = 0
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RECEIPT_BYTES) {
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

    fun delete(path: String?) {
        if (path == null) return
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(rootDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    companion object {
        const val RECEIPT_ROOT_DIR = "local_receipts"
        const val MAX_RECEIPT_BYTES = 10 * 1024 * 1024
    }
}
