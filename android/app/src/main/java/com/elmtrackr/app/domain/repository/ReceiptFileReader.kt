package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.ReceiptUpload

/**
 * Reads a stored receipt image back off the device so it can be uploaded.
 *
 * Exists so the sync pipeline can retry an upload without depending on
 * `PhotoFileManager`, which needs a `Context` and a FileProvider authority — and
 * so tests can drive the retry with a fake instead of a filesystem.
 */
interface ReceiptFileReader {
    /** Null when the file is missing, empty, or too large to upload. */
    suspend fun toReceiptUpload(path: String): ReceiptUpload?
}
