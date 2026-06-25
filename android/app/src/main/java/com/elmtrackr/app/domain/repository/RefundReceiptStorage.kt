package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundDirection

interface RefundReceiptStorage {
    suspend fun upload(
        userId: String,
        shiftId: String,
        direction: RefundDirection,
        receipt: ReceiptUpload,
    ): String

    suspend fun createSignedUrl(path: String): String

    suspend fun delete(path: String)
}
