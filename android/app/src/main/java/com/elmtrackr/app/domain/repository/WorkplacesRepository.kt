package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.Workplace
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Workplaces and the leave arrangement attached to each.
 *
 * Policies live here rather than in their own repository because they are edited
 * on the workplace's settings screen and have no meaning apart from it: a leave
 * policy is a property of a job.
 */
interface WorkplacesRepository {

    fun observeWorkplaces(userId: String): Flow<List<Workplace>>

    /** Includes archived jobs, so history from a job the user has left stays readable. */
    fun observeAllWorkplaces(userId: String): Flow<List<Workplace>>

    suspend fun getWorkplaces(userId: String): List<Workplace>

    suspend fun getWorkplace(userId: String, workplaceId: String): Workplace?

    suspend fun getDefaultWorkplace(userId: String): Workplace?

    /**
     * The user's first workplace, created from their default compensation profile
     * and adopting the shifts and profiles that predate workplaces.
     *
     * Lazily, on first use, rather than during the database upgrade: writing a
     * workplace into historical rows as part of a migration would rewrite the
     * user's recorded history invisibly. Idempotent — a user who already has a
     * workplace gets theirs back.
     */
    suspend fun ensureDefaultWorkplace(userId: String): Workplace?

    suspend fun upsertWorkplace(workplace: Workplace): Workplace

    /**
     * Archives rather than deletes. Leave already reported against the job stays
     * in the history and in past months' reports; a job that ended is not a job
     * that never happened.
     */
    suspend fun archiveWorkplace(userId: String, workplaceId: String)

    // ── Leave policy ──────────────────────────────────────────────────────────

    fun observePolicies(userId: String): Flow<List<LeavePolicy>>

    /**
     * The policy in force at [on] for this workplace, or null when the workplace
     * has none. Effective-dated so that an absence reported for last month is
     * priced by the policy that applied then, not by today's.
     */
    suspend fun resolvePolicy(workplaceId: String, on: Instant): LeavePolicy?

    /** The current policy, creating one from the region preset if there is none. */
    suspend fun ensurePolicy(userId: String, workplaceId: String): LeavePolicy

    /**
     * Supersedes the current policy with a new effective-dated one when the rules
     * change, so historical estimates stay explainable. Editing the policy that
     * is already in force in place would restate them.
     */
    suspend fun updatePolicyRules(userId: String, workplaceId: String, rules: LeavePolicyRules): LeavePolicy
}
