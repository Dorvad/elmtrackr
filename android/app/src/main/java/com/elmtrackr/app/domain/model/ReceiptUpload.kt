package com.elmtrackr.app.domain.model

data class ReceiptUpload(
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String? = null,
)
