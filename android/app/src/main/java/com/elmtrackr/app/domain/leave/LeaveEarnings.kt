package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import java.time.LocalDate

/**
 * Paid leave for one month, per workplace and in total.
 *
 * Deliberately a sibling of the worked-hours report rather than part of it.
 * `MonthlyReport` guarantees that regular + overtime + weekend minutes equal the
 * total, and every consumer — the distribution bar, the CSV totals row, the PDF
 * header — relies on that. Paid leave has no minutes worked, so adding it there
 * would break the invariant and inflate hours the user did not work. The two
 * meet only where the screen adds gross figures together for an estimated total.
 *
 * Days and hours are kept in separate fields instead of being normalised. A
 * month may legitimately contain both, and converting them into one unit needs a
 * standard day the workplace may not have defined.
 */
data class MonthlyLeaveEarnings(
    val year: Int,
    val month: Int,
    val currencyCode: String,
    val vacationGross: Double = 0.0,
    val sickGross: Double = 0.0,
    val vacationDays: Double = 0.0,
    val vacationHours: Double = 0.0,
    val sickDays: Double = 0.0,
    val sickHours: Double = 0.0,
    val byWorkplace: List<WorkplaceLeaveEarnings> = emptyList(),
) {
    val paidLeaveGross: Double get() = vacationGross + sickGross
    val hasVacation: Boolean get() = vacationDays > 0.0 || vacationHours > 0.0 || vacationGross > 0.0
    val hasSick: Boolean get() = sickDays > 0.0 || sickHours > 0.0 || sickGross > 0.0
    val isEmpty: Boolean get() = !hasVacation && !hasSick
}

data class WorkplaceLeaveEarnings(
    val workplaceId: String,
    val workplaceName: String,
    val vacationGross: Double = 0.0,
    val sickGross: Double = 0.0,
    val vacationDays: Double = 0.0,
    val vacationHours: Double = 0.0,
    val sickDays: Double = 0.0,
    val sickHours: Double = 0.0,
) {
    val paidLeaveGross: Double get() = vacationGross + sickGross
}

/** One absence row for the history feed, alongside — never labelled as — a shift. */
data class AbsenceListEntry(
    val allocation: AbsenceAllocation,
    val event: AbsenceEvent,
    val workplaceName: String,
    val currencyCode: String,
) {
    val date: LocalDate get() = allocation.affectedDate
    val type: AbsenceType get() = event.type
}

object LeaveEarningsBuilder {

    /**
     * [allocations] must be the month's rows, already excluding deleted ones.
     * [typeOf] resolves an allocation's leave type from its event, and
     * [workplaceNameOf] its workplace label; both are passed in so this stays a
     * pure function over data the caller already holds.
     */
    fun buildMonthly(
        year: Int,
        month: Int,
        currencyCode: String,
        allocations: List<AbsenceAllocation>,
        typeOf: (AbsenceAllocation) -> AbsenceType?,
        workplaceNameOf: (String) -> String,
    ): MonthlyLeaveEarnings {
        var vacationGross = 0.0
        var sickGross = 0.0
        var vacationDays = 0.0
        var vacationHours = 0.0
        var sickDays = 0.0
        var sickHours = 0.0
        val perWorkplace = LinkedHashMap<String, WorkplaceLeaveEarnings>()

        for (allocation in allocations) {
            // An allocation whose event cannot be resolved is skipped rather than
            // counted as one type or the other: putting a sick day into the
            // vacation column would be worse than omitting it, and the row is
            // still visible in the history list.
            val type = typeOf(allocation) ?: continue
            val days = if (allocation.unit == LeaveBalanceUnit.DAYS) allocation.entitlementUnits else 0.0
            val hours = if (allocation.unit == LeaveBalanceUnit.HOURS) allocation.entitlementUnits else 0.0
            val gross = allocation.estimatedGrossPay

            val existing = perWorkplace[allocation.workplaceId] ?: WorkplaceLeaveEarnings(
                workplaceId = allocation.workplaceId,
                workplaceName = workplaceNameOf(allocation.workplaceId),
            )
            perWorkplace[allocation.workplaceId] = when (type) {
                AbsenceType.VACATION -> existing.copy(
                    vacationGross = existing.vacationGross + gross,
                    vacationDays = existing.vacationDays + days,
                    vacationHours = existing.vacationHours + hours,
                )

                AbsenceType.SICK -> existing.copy(
                    sickGross = existing.sickGross + gross,
                    sickDays = existing.sickDays + days,
                    sickHours = existing.sickHours + hours,
                )
            }

            when (type) {
                AbsenceType.VACATION -> {
                    vacationGross += gross
                    vacationDays += days
                    vacationHours += hours
                }

                AbsenceType.SICK -> {
                    sickGross += gross
                    sickDays += days
                    sickHours += hours
                }
            }
        }

        return MonthlyLeaveEarnings(
            year = year,
            month = month,
            currencyCode = currencyCode,
            vacationGross = vacationGross,
            sickGross = sickGross,
            vacationDays = vacationDays,
            vacationHours = vacationHours,
            sickDays = sickDays,
            sickHours = sickHours,
            byWorkplace = perWorkplace.values.toList(),
        )
    }
}
