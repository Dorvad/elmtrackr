package com.elmtrackr.app.domain.refund

import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

data class DeleteRefundClaimResult(
    val receiptDeleteFailed: Boolean,
    val localReceiptDeleteFailed: Boolean = false,
)

class DeleteRefundClaim @Inject constructor(
    private val refundsRepository: RefundsRepository,
    private val shiftsRepository: ShiftsRepository,
    private val refundReceiptStorage: RefundReceiptStorage?,
    private val receiptsRepository: ReceiptsRepository,
) {
    suspend operator fun invoke(claimId: String): DeleteRefundClaimResult {
        val claim = refundsRepository.getClaimById(claimId) ?: return DeleteRefundClaimResult(false)
        val receiptPath = claim.receiptPath

        refundsRepository.deleteClaim(claimId)

        val remaining = refundsRepository.observeClaimsForShift(claim.shiftId).first()
        shiftsRepository.getShiftById(claim.shiftId)?.let { shift ->
            shiftsRepository.updateShift(
                shift.copy(
                    refundAction = if (remaining.isEmpty()) null else RefundAction.SUBMITTED,
                    updatedAt = Instant.now(),
                ),
            )
        }

        val receiptDeleteFailed = receiptPath?.let { path ->
            refundReceiptStorage?.let { storage ->
                runCatching { storage.delete(path) }.isFailure
            } ?: false
        } ?: false

        val localReceiptDeleteFailed = receiptsRepository.deleteByRefundClaimId(claimId)

        return DeleteRefundClaimResult(
            receiptDeleteFailed = receiptDeleteFailed,
            localReceiptDeleteFailed = localReceiptDeleteFailed,
        )
    }
}
