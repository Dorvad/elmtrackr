package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.repository.RefundReceiptStorage

class FakeRefundReceiptStorage : RefundReceiptStorage {
    var failUploads = false
    val uploads = mutableMapOf<String, ReceiptUpload>()
    val deletedPaths = mutableListOf<String>()
    var onDeletePath: ((String) -> Unit)? = null

    override suspend fun upload(
        userId: String,
        shiftId: String,
        direction: RefundDirection,
        receipt: ReceiptUpload,
    ): String {
        if (failUploads) error("Receipt bucket unavailable")
        val path = "$userId/$shiftId/${direction.name.lowercase()}/${receipt.fileName}"
        uploads[path] = receipt
        return path
    }

    override suspend fun createSignedUrl(path: String): String = "https://example.test/$path"

    override suspend fun delete(path: String) {
        onDeletePath?.invoke(path)
        uploads.remove(path)
        deletedPaths += path
    }
}
