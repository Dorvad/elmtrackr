package com.elmtrackr.app.ui.refunds

import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.Shift

data class RefundClaimUiState(
    val shift: Shift? = null,
    val claims: List<RefundClaim> = emptyList(),
    val toEligibility: RefundPolicy.Eligibility? = null,
    val fromEligibility: RefundPolicy.Eligibility? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val deletingClaimId: String? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val form: RefundClaimFormUiState? = null,
    val camera: ReceiptCameraUiState? = null,
    val receiptReview: ReceiptReviewUiState? = null,
    val isProcessingReceipt: Boolean = false,
    val launchDocumentScanner: Boolean = false,
)

data class RefundClaimFormUiState(
    val claimId: String? = null,
    val direction: RefundDirection,
    val provider: RefundProvider = RefundProvider.LIME,
    val amountText: String = "",
    val rideAtMillis: Long,
    val notes: String = "",
    val existingReceiptPath: String? = null,
    val pendingPhotoPath: String? = null,
    val pendingPhotoName: String? = null,
    val linkedReceiptId: String? = null,
    val localReceiptImagePath: String? = null,
)

data class ReceiptCameraUiState(
    val outputPath: String,
)
