package com.elmtrackr.app.domain.refund

import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.repository.RefundsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRefundClaimsForShift @Inject constructor(
    private val refundsRepository: RefundsRepository,
) {
    operator fun invoke(shiftId: String): Flow<List<RefundClaim>> =
        refundsRepository.observeClaimsForShift(shiftId)
}
