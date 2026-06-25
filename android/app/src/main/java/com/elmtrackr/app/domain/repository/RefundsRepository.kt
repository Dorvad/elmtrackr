package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RefundsRepository {

    fun observeClaimsForUser(userId: String): Flow<List<RefundClaim>>

    fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaim>>

    suspend fun getClaimById(localId: String): RefundClaim?

    suspend fun addClaim(
        shiftLocalId: String,
        userId: String,
        direction: RefundDirection,
        provider: RefundProvider,
        amount: Double,
        notes: String? = null,
        rideAt: Instant = Instant.now(),
        receiptPath: String? = null,
    ): RefundClaim

    suspend fun updateClaim(claim: RefundClaim): RefundClaim

    suspend fun deleteClaim(localId: String)

    fun observePendingSyncClaims(userId: String): Flow<List<RefundClaim>>
}
