package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.leave.LeaveBalanceEstimate
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * One date of an absence at one workplace, as the form collects it before the
 * engine prices it.
 */
data class AbsenceDayInput(
    val workplaceId: String,
    val date: LocalDate,
    val entitlementUnits: Double = 1.0,
    val unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
    val expectedWorkMinutes: Int? = null,
    /** Set when the app could not value the day and the user supplied an amount. */
    val manualDailyAmount: Double? = null,
    val manualReason: String? = null,
)

/** An absence and the days it affects, ready to save. */
data class AbsenceDraft(
    val type: AbsenceType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null,
    val days: List<AbsenceDayInput>,
)

/** One day of a draft, priced but not saved, for the preview the form shows. */
data class AbsenceDayPreview(
    val workplaceId: String,
    val date: LocalDate,
    val estimate: com.elmtrackr.app.domain.leave.LeaveEstimate,
)

interface LeaveRepository {

    /**
     * Prices a draft without saving it, through the same path a save uses.
     *
     * The same path deliberately: a preview computed differently from the save is
     * a preview that can disagree with what gets stored, and the number the user
     * agreed to is the one they will look for on their payslip.
     */
    suspend fun previewAbsence(userId: String, draft: AbsenceDraft): List<AbsenceDayPreview>

    /** Dates the user has worked at a workplace, for proposing which days an absence hits. */
    suspend fun workedDatesForWorkplace(userId: String, workplaceId: String): List<LocalDate>

    /** Duplicate leave, a shift on the same day, an archived job, an adjacent illness. */
    suspend fun detectConflicts(
        userId: String,
        draft: AbsenceDraft,
        excludeEventId: String? = null,
    ): List<com.elmtrackr.app.domain.leave.LeaveConflict>

    fun observeEvents(userId: String): Flow<List<AbsenceEvent>>

    fun observeAllocations(userId: String): Flow<List<AbsenceAllocation>>

    /** The month's allocations, for the reports breakdown and the history feed. */
    fun observeAllocationsInMonth(userId: String, year: Int, month: Int): Flow<List<AbsenceAllocation>>

    suspend fun getEvent(userId: String, eventId: String): AbsenceEvent?

    suspend fun getAllocationsForEvent(eventId: String): List<AbsenceAllocation>

    /**
     * Saves an absence and prices each affected day, then returns the event.
     *
     * The event and its allocations are written in one transaction: a half-saved
     * absence would deduct from a balance without appearing in the history, or
     * appear without deducting.
     */
    suspend fun saveAbsence(userId: String, draft: AbsenceDraft): AbsenceEvent

    /**
     * Replaces an absence's dates and rebuilds every allocation from the policy
     * and rate in force now.
     *
     * A rebuild, not a patch: the affected dates, the sick-day ordinals and
     * therefore the tier multipliers all shift when the range changes, so an
     * allocation that merely had its date edited would keep a multiplier computed
     * for a different day of the illness.
     */
    suspend fun updateAbsence(userId: String, eventId: String, draft: AbsenceDraft): AbsenceEvent?

    suspend fun deleteAbsence(userId: String, eventId: String)

    /** Merges [sourceEventId] into [targetEventId] as one continuous period, on the user's say-so. */
    suspend fun mergeSickPeriods(userId: String, targetEventId: String, sourceEventId: String): AbsenceEvent?

    // ── Payslip balances ──────────────────────────────────────────────────────

    fun observeBalanceSnapshots(userId: String): Flow<List<LeaveBalanceSnapshot>>

    suspend fun getBalanceHistory(workplaceId: String, type: AbsenceType): List<LeaveBalanceSnapshot>

    /**
     * Records a balance read off a payslip as a new snapshot. Never an update:
     * entering August's payslip must leave July's in place, because the estimate
     * is "the last official number minus what has been reported since it".
     */
    suspend fun addBalanceSnapshot(snapshot: LeaveBalanceSnapshot): LeaveBalanceSnapshot

    suspend fun deleteBalanceSnapshot(userId: String, snapshotId: String)

    /** Official balance, usage since it, and the subtraction — kept as three numbers. */
    suspend fun estimateBalance(
        userId: String,
        workplaceId: String,
        type: AbsenceType,
    ): LeaveBalanceEstimate

    fun observeBalanceEstimates(userId: String): Flow<List<LeaveBalanceEstimate>>
}
