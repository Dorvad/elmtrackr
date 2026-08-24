package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.leave.LeavePresets
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.domain.repository.WorkplacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * In-memory workplaces and leave policies.
 *
 * Policies are effective-dated in production: [updatePolicyRules] closes the
 * outgoing one and writes a new row, so a past absence keeps the explanation it
 * was priced with. This fake keeps that behaviour rather than overwriting in
 * place — a test that asserts "the old policy is still readable" should pass or
 * fail on the real rule, not on a shortcut taken here.
 */
class FakeWorkplacesRepository : WorkplacesRepository {

    private val workplaces = MutableStateFlow<List<Workplace>>(emptyList())
    private val policies = MutableStateFlow<List<LeavePolicy>>(emptyList())

    /** Region used for a workplace this fake has to invent. */
    var defaultRegion: RegionCode = RegionCode.IL

    fun setWorkplaces(vararg items: Workplace) {
        workplaces.value = items.toList()
    }

    fun setPolicies(vararg items: LeavePolicy) {
        policies.value = items.toList()
    }

    fun policiesFor(workplaceId: String): List<LeavePolicy> =
        policies.value.filter { it.workplaceId == workplaceId }

    override fun observeWorkplaces(userId: String): Flow<List<Workplace>> =
        workplaces.map { list -> list.filter { it.userId == userId && !it.isArchived } }

    override fun observeAllWorkplaces(userId: String): Flow<List<Workplace>> =
        workplaces.map { list -> list.filter { it.userId == userId } }

    override suspend fun getWorkplaces(userId: String): List<Workplace> =
        workplaces.value.filter { it.userId == userId }

    override suspend fun getWorkplace(userId: String, workplaceId: String): Workplace? =
        workplaces.value.firstOrNull { it.userId == userId && it.id == workplaceId }

    override suspend fun getDefaultWorkplace(userId: String): Workplace? =
        workplaces.value.firstOrNull { it.userId == userId && it.isDefault }
            ?: workplaces.value.firstOrNull { it.userId == userId }

    override suspend fun ensureDefaultWorkplace(userId: String): Workplace {
        getDefaultWorkplace(userId)?.let { return it }
        val created = Workplace(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = "Main job",
            regionCode = defaultRegion,
            currencyCode = "ILS",
            timezone = "UTC",
            isDefault = true,
        )
        workplaces.value = workplaces.value + created
        return created
    }

    override suspend fun upsertWorkplace(workplace: Workplace): Workplace {
        val id = workplace.id.ifBlank { UUID.randomUUID().toString() }
        val saved = workplace.copy(id = id)
        workplaces.value = workplaces.value.filter { it.id != id } + saved
        return saved
    }

    override suspend fun archiveWorkplace(userId: String, workplaceId: String) {
        workplaces.value = workplaces.value.map {
            if (it.userId == userId && it.id == workplaceId) it.copy(isArchived = true) else it
        }
    }

    override fun observePolicies(userId: String): Flow<List<LeavePolicy>> =
        policies.map { list -> list.filter { it.userId == userId } }

    override suspend fun resolvePolicy(workplaceId: String, on: Instant): LeavePolicy? =
        policies.value
            .filter { it.workplaceId == workplaceId }
            .sortedByDescending { it.effectiveFrom }
            .firstOrNull { !it.effectiveFrom.isAfter(on) && (it.effectiveUntil?.isAfter(on) ?: true) }
            ?: policies.value.firstOrNull { it.workplaceId == workplaceId }

    override suspend fun ensurePolicy(userId: String, workplaceId: String): LeavePolicy {
        resolvePolicy(workplaceId, Instant.now())?.let { return it }
        val region = workplaces.value.firstOrNull { it.id == workplaceId }?.regionCode ?: defaultRegion
        val created = LeavePolicy(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workplaceId = workplaceId,
            regionCode = region,
            rules = LeavePresets.forRegion(region),
            effectiveFrom = Instant.now(),
            isActive = true,
        )
        policies.value = policies.value + created
        return created
    }

    override suspend fun updatePolicyRules(
        userId: String,
        workplaceId: String,
        rules: LeavePolicyRules,
    ): LeavePolicy {
        val now = Instant.now()
        val current = resolvePolicy(workplaceId, now)
        val next = LeavePolicy(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workplaceId = workplaceId,
            regionCode = current?.regionCode ?: defaultRegion,
            rules = rules,
            effectiveFrom = now,
            isActive = true,
        )
        policies.value = policies.value.map { policy ->
            if (policy.id == current?.id) {
                policy.copy(effectiveUntil = now, isActive = false)
            } else {
                policy
            }
        } + next
        return next
    }
}
