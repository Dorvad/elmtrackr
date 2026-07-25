package com.elmtrackr.app.domain.refund

import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import java.time.Instant
import javax.inject.Inject

data class RefundClaimUpsertInput(
    val shiftId: String,
    val claimId: String? = null,
    val direction: RefundDirection,
    val provider: RefundProvider,
    val amount: Double,
    val rideAt: Instant,
    val notes: String,
    val receipt: ReceiptUpload? = null,
)

data class RefundClaimUpsertResult(
    val claim: RefundClaim,
    val receiptUploadFailed: Boolean,
)

class UpsertRefundClaim @Inject constructor(
    private val refundsRepository: RefundsRepository,
    private val shiftsRepository: ShiftsRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val refundReceiptStorage: RefundReceiptStorage?,
) {
    suspend operator fun invoke(input: RefundClaimUpsertInput): RefundClaimUpsertResult {
        require(input.amount > 0.0) { "Enter a valid refund amount" }

        val userId = currentUserProvider.currentUserId()
            ?: error("Sign in before saving a claim")
        val shift = shiftsRepository.getShiftById(input.shiftId)
            ?: error("Shift not found")

        // A null claimId always creates a new claim: a shift may hold any
        // number of rides per direction, so there is no fallback lookup that
        // would silently turn an "add another ride" into an edit.
        val existing = input.claimId?.let { refundsRepository.getClaimById(it) }

        val oldReceiptPath = existing?.receiptPath
        var uploadFailed = false
        val receiptPath = if (input.receipt != null) {
            val uploaded = refundReceiptStorage?.let { storage ->
                runCatching {
                    storage.upload(userId, input.shiftId, input.direction, input.receipt)
                }.getOrNull()
            }
            if (uploaded == null) {
                uploadFailed = true
                oldReceiptPath
            } else {
                uploaded
            }
        } else {
            oldReceiptPath
        }

        val saved = if (existing == null) {
            refundsRepository.addClaim(
                shiftLocalId = input.shiftId,
                userId = userId,
                direction = input.direction,
                provider = input.provider,
                amount = input.amount,
                notes = input.notes.ifBlank { null },
                rideAt = input.rideAt,
                receiptPath = receiptPath,
            )
        } else {
            refundsRepository.updateClaim(
                existing.copy(
                    provider = input.provider,
                    amount = input.amount,
                    rideAt = input.rideAt,
                    notes = input.notes.ifBlank { null },
                    receiptPath = receiptPath,
                    updatedAt = Instant.now(),
                ),
            )
        }

        if (oldReceiptPath != null && oldReceiptPath != saved.receiptPath) {
            runCatching { refundReceiptStorage?.delete(oldReceiptPath) }
        }

        shiftsRepository.updateShift(
            shift.copy(
                refundAction = RefundAction.SUBMITTED,
                updatedAt = Instant.now(),
            ),
        )

        return RefundClaimUpsertResult(saved, uploadFailed)
    }
}
