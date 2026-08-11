package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceType
import java.time.LocalDate

/**
 * What the app has to say about a leave entry before it is saved.
 *
 * The distinction between the two severities is the point. Two leave entries on
 * the same date at the same workplace double-count a balance and are almost
 * certainly a mistake, so they block. A work shift on the same date does not:
 * working the morning and taking the afternoon off is ordinary, and refusing it
 * would force the user to lie about one or the other. That gets a warning saying
 * what to check.
 */
sealed interface LeaveConflict {
    val date: LocalDate

    /** Must be resolved before saving. */
    data class DuplicateLeave(
        override val date: LocalDate,
        val existingType: AbsenceType,
    ) : LeaveConflict

    /** Shown, then the user decides. */
    data class WorkShiftSameDate(
        override val date: LocalDate,
        val workedMinutes: Int,
    ) : LeaveConflict

    /** Shown, then the user decides. */
    data class ArchivedWorkplace(override val date: LocalDate) : LeaveConflict

    /** Shown, then the user decides — offers to merge into one illness. */
    data class AdjacentSickPeriod(
        override val date: LocalDate,
        val existingEventId: String,
        val existingStart: LocalDate,
        val existingEnd: LocalDate,
    ) : LeaveConflict
}

/** A date already carrying leave at the workplace being reported against. */
data class ExistingLeaveDate(val date: LocalDate, val type: AbsenceType)

/** A date the user has recorded worked time on at that workplace. */
data class WorkedDate(val date: LocalDate, val workedMinutes: Int)

/** An existing sick period, for the adjacency check. */
data class SickPeriod(val eventId: String, val start: LocalDate, val end: LocalDate)

object LeaveConflictDetector {

    fun detect(
        dates: List<LocalDate>,
        type: AbsenceType,
        existingLeave: List<ExistingLeaveDate>,
        workedDates: List<WorkedDate>,
        existingSickPeriods: List<SickPeriod> = emptyList(),
        workplaceIsArchived: Boolean = false,
    ): List<LeaveConflict> {
        if (dates.isEmpty()) return emptyList()
        val leaveByDate = existingLeave.associateBy { it.date }
        val workByDate = workedDates.associateBy { it.date }

        val conflicts = buildList {
            for (date in dates) {
                leaveByDate[date]?.let { add(LeaveConflict.DuplicateLeave(date, it.type)) }
                workByDate[date]?.let { add(LeaveConflict.WorkShiftSameDate(date, it.workedMinutes)) }
                if (workplaceIsArchived) add(LeaveConflict.ArchivedWorkplace(date))
            }
        }

        if (type != AbsenceType.SICK) return conflicts
        return conflicts + adjacentSickPeriods(dates, existingSickPeriods)
    }

    /**
     * A new sick entry touching an existing one is very likely the same illness
     * carried on, and that matters: the ordinal day drives the pay ladder, so two
     * separate periods restart at 0% and 50% where one continuous period would
     * have reached full pay.
     *
     * Detected and offered, never applied. Merging silently would rewrite an
     * estimate the user has already seen, and two genuinely separate illnesses a
     * day apart do happen.
     */
    fun adjacentSickPeriods(
        dates: List<LocalDate>,
        existing: List<SickPeriod>,
    ): List<LeaveConflict.AdjacentSickPeriod> {
        if (dates.isEmpty()) return emptyList()
        val start = dates.min()
        val end = dates.max()
        return existing
            .filter { period ->
                // Touching or overlapping: the day after an existing period, the
                // day before it, or any shared date.
                !period.start.isAfter(end.plusDays(1)) && !period.end.isBefore(start.minusDays(1))
            }
            .map { period ->
                LeaveConflict.AdjacentSickPeriod(
                    date = start,
                    existingEventId = period.eventId,
                    existingStart = period.start,
                    existingEnd = period.end,
                )
            }
    }

    /** Only duplicate leave stops a save. */
    fun blocking(conflicts: List<LeaveConflict>): List<LeaveConflict.DuplicateLeave> =
        conflicts.filterIsInstance<LeaveConflict.DuplicateLeave>()
}
