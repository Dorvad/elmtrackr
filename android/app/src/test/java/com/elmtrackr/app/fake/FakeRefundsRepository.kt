package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.repository.RefundsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class FakeRefundsRepository : RefundsRepository {
    private val claims = MutableStateFlow<List<RefundClaim>>(emptyList())
    var onDeleteClaim: ((String) -> Unit)? = null
    override fun observeClaimsForUser(userId: String): Flow<List<RefundClaim>> = claims.map { list -> list.filter { it.userId == userId } }
    override fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaim>> = claims.map { list -> list.filter { it.shiftId == shiftLocalId } }
    override suspend fun getClaimById(localId: String): RefundClaim? = claims.value.firstOrNull { it.id == localId }
    override suspend fun addClaim(shiftLocalId: String, userId: String, direction: RefundDirection, provider: RefundProvider, amount: Double, notes: String?, rideAt: Instant, receiptPath: String?): RefundClaim {
        val claim = RefundClaim(UUID.randomUUID().toString(), shiftLocalId, userId, direction, provider, amount, rideAt, notes, receiptPath)
        claims.value += claim
        return claim
    }
    override suspend fun updateClaim(claim: RefundClaim): RefundClaim {
        claims.value = claims.value.map { if (it.id == claim.id) claim else it }
        return claim
    }
    override suspend fun deleteClaim(localId: String) {
        onDeleteClaim?.invoke(localId)
        claims.value = claims.value.filterNot { it.id == localId }
    }
}
