package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import kotlinx.coroutines.flow.Flow

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
    ): RefundClaim

    suspend fun updateClaim(claim: RefundClaim): RefundClaim

    suspend fun deleteClaim(localId: String)

    fun observePendingSyncClaims(): Flow<List<RefundClaim>>
}
