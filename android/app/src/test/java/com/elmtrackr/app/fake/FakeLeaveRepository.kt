package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.leave.LeaveBalanceEstimate
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.repository.AbsenceDayPreview
import com.elmtrackr.app.domain.repository.AbsenceDraft
import com.elmtrackr.app.domain.repository.LeaveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory absences for report tests.
 *
 * Month filtering matches [LeaveRepository.observeAllocationsInMonth]: a vacation
 * day belongs to the month of its affected date, not to the month the user
 * happened to be looking at when they saved it.
 */
class FakeLeaveRepository : LeaveRepository {

    private val events = MutableStateFlow<List<AbsenceEvent>>(emptyList())
    private val allocations = MutableStateFlow<List<AbsenceAllocation>>(emptyList())

    fun setEvents(vararg items: AbsenceEvent) {
        events.value = items.toList()
    }

    fun setAllocations(vararg items: AbsenceAllocation) {
        allocations.value = items.toList()
    }

    override fun observeEvents(userId: String): Flow<List<AbsenceEvent>> =
        events.map { list -> list.filter { it.userId == userId } }

    override fun observeAllocations(userId: String): Flow<List<AbsenceAllocation>> =
        allocations.map { list -> list.filter { it.userId == userId } }

    override fun observeAllocationsInMonth(
        userId: String,
        year: Int,
        month: Int,
    ): Flow<List<AbsenceAllocation>> = allocations.map { list ->
        list.filter {
            it.userId == userId &&
                it.affectedDate.year == year &&
                it.affectedDate.monthValue == month
        }
    }

    override suspend fun getEvent(userId: String, eventId: String): AbsenceEvent? =
        events.value.firstOrNull { it.userId == userId && it.id == eventId }

    override suspend fun getAllocationsForEvent(eventId: String): List<AbsenceAllocation> =
        allocations.value.filter { it.absenceEventId == eventId }

    override fun observeBalanceSnapshots(userId: String): Flow<List<LeaveBalanceSnapshot>> =
        flowOf(emptyList())

    override fun observeBalanceEstimates(userId: String): Flow<List<LeaveBalanceEstimate>> =
        flowOf(emptyList())

    override suspend fun previewAbsence(userId: String, draft: AbsenceDraft): List<AbsenceDayPreview> =
        error("not used")

    override suspend fun workedDatesForWorkplace(userId: String, workplaceId: String): List<LocalDate> =
        emptyList()

    override suspend fun detectConflicts(
        userId: String,
        draft: AbsenceDraft,
        excludeEventId: String?,
    ): List<com.elmtrackr.app.domain.leave.LeaveConflict> = emptyList()

    override suspend fun saveAbsence(userId: String, draft: AbsenceDraft): AbsenceEvent =
        error("not used")

    override suspend fun updateAbsence(
        userId: String,
        eventId: String,
        draft: AbsenceDraft,
    ): AbsenceEvent? = error("not used")

    override suspend fun deleteAbsence(userId: String, eventId: String) = error("not used")

    override suspend fun mergeSickPeriods(
        userId: String,
        targetEventId: String,
        sourceEventId: String,
    ): AbsenceEvent? = error("not used")

    override suspend fun getBalanceHistory(
        workplaceId: String,
        type: AbsenceType,
    ): List<LeaveBalanceSnapshot> = emptyList()

    override suspend fun addBalanceSnapshot(snapshot: LeaveBalanceSnapshot): LeaveBalanceSnapshot =
        error("not used")

    override suspend fun deleteBalanceSnapshot(userId: String, snapshotId: String) = error("not used")

    override suspend fun estimateBalance(
        userId: String,
        workplaceId: String,
        type: AbsenceType,
    ): LeaveBalanceEstimate = error("not used")
}
