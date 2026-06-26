package com.elmtrackr.app.data.remote

import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.minutes

class SupabaseRefundReceiptStorage(
    private val client: SupabaseClient,
) : RefundReceiptStorage {

    private val bucket get() = client.storage.from(BUCKET)

    override suspend fun upload(
        userId: String,
        shiftId: String,
        direction: RefundDirection,
        receipt: ReceiptUpload,
    ): String {
        val extension = receipt.fileName.substringAfterLast('.', "jpg")
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .ifBlank { "jpg" }
        val path = "$userId/$shiftId/${direction.name.lowercase()}/${System.currentTimeMillis()}.$extension"
        val mimeType = receipt.mimeType ?: when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "image/jpeg"
        }
        bucket.upload(path, receipt.bytes) {
            upsert = true
            contentType = ContentType.parse(mimeType)
        }
        return path
    }

    override suspend fun createSignedUrl(path: String): String =
        bucket.createSignedUrl(path, 10.minutes)

    override suspend fun delete(path: String) {
        bucket.delete(path)
    }

    private companion object {
        const val BUCKET = "refund-receipts"
    }
}
