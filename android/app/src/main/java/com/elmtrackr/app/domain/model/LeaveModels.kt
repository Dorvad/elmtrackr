package com.elmtrackr.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A job. Deliberately not the same thing as a [CompensationProfile].
 *
 * A profile answers "how is worked time paid", and it changes: a raise, a new
 * overtime arrangement, or a new effective-dated profile. Leave entitlement,
 * seniority and the balances printed on a payslip belong to the *employer* and
 * survive all of that, so they hang off this instead.
 *
 * Employer identity is intentionally minimal. Nothing in the feature needs a
 * company number, an address or an HR contact, so none is asked for.
 */
data class Workplace(
    val id: String,
    val userId: String,
    val name: String,
    val regionCode: RegionCode,
    val currencyCode: String,
    val timezone: String,
    /** Epoch day. Recorded when the user knows it; no entitlement is derived from it yet. */
    val employmentStartDate: LocalDate? = null,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
)

enum class AbsenceType {
    SICK,
    VACATION,
    ;

    /** Wire value, matching the `absence_events.type` check constraint. */
    val persistedValue: String get() = name.lowercase()

    companion object {
        /**
         * Null for anything unrecognised rather than a fallback. Guessing between
         * sick and vacation would put a day against the wrong balance and pay it
         * under the wrong policy, which is worse than skipping a row the app did
         * not write.
         */
        fun fromPersisted(raw: String?): AbsenceType? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized }
        }
    }
}

enum class LeaveBalanceUnit {
    DAYS,
    HOURS,
    ;

    val persistedValue: String get() = name.lowercase()

    companion object {
        fun fromPersisted(raw: String?): LeaveBalanceUnit {
            if (raw.isNullOrBlank()) return DAYS
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: DAYS
        }
    }
}

enum class LeaveBalanceSource {
    PAYSLIP,
    MANUAL,
    ;

    val persistedValue: String get() = name.lowercase()

    companion object {
        /** MANUAL is the weaker claim of the two, so it is the safe fallback. */
        fun fromPersisted(raw: String?): LeaveBalanceSource {
            if (raw.isNullOrBlank()) return MANUAL
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: MANUAL
        }
    }
}

/** How a sick day's expected pay is valued before the tier multiplier applies. */
enum class SickPayBasis {
    /** Average pay per day actually worked over the preceding three months. */
    HISTORICAL_AVERAGE,

    /** The hours the user says they were scheduled to work on that date. */
    SCHEDULED_HOURS,

    /** A fixed standard day from the policy. */
    FIXED_DAILY_HOURS,

    /** The user states the amount. */
    MANUAL,
    ;

    val persistedValue: String get() = name.lowercase()

    companion object {
        fun fromPersisted(raw: String?): SickPayBasis {
            if (raw.isNullOrBlank()) return HISTORICAL_AVERAGE
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: HISTORICAL_AVERAGE
        }
    }
}

enum class VacationPayBasis {
    /**
     * Eligible gross earnings across a three-month period divided by 90 — the
     * shape commonly used for hourly and daily workers in Israel. Presented as
     * an estimate with the period it used, never as a payroll figure.
     */
    ISRAEL_STATUTORY_AVERAGE_90,

    /** Eligible gross over the period divided by the days actually worked in it. */
    ACTUAL_WORKDAYS_AVERAGE,
    SCHEDULED_HOURS,
    FIXED_DAILY_HOURS,
    MANUAL,
    ;

    val persistedValue: String get() = name.lowercase()

    companion object {
        fun fromPersisted(raw: String?): VacationPayBasis {
            if (raw.isNullOrBlank()) return ISRAEL_STATUTORY_AVERAGE_90
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: ISRAEL_STATUTORY_AVERAGE_90
        }
    }
}

/**
 * One rung of the sick pay ladder: from [fromDay] of the illness (inclusive) to
 * [toDay] (inclusive, open-ended when null), pay [multiplier] of the expected
 * day.
 *
 * The Israeli default is expressed as three of these rather than as code, so a
 * workplace with a better agreement replaces it with a single 100% rung. No UI
 * or calculator branches on "is this Israel" to decide what a sick day pays.
 */
data class SickPayTier(
    val fromDay: Int,
    val toDay: Int? = null,
    val multiplier: Double,
)

data class SickLeavePolicy(
    val enabled: Boolean = true,
    val payTiers: List<SickPayTier> = emptyList(),
    val payBasis: SickPayBasis = SickPayBasis.HISTORICAL_AVERAGE,
    val fixedDailyMinutes: Int? = null,
    /**
     * Off in V1 and read only as reference information. Accruing correctly for a
     * part-time or irregular hourly worker depends on the actual work pattern,
     * partial months, seniority and sector agreements; a wrong automatic number
     * shown as a balance is worse than no number.
     */
    val accrualEnabled: Boolean = false,
    val accrualDaysPerMonth: Double? = null,
    val maxAccruedDays: Double? = null,
)

data class VacationLeavePolicy(
    val enabled: Boolean = true,
    val payBasis: VacationPayBasis = VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90,
    val fixedDailyMinutes: Int? = null,
    val accrualEnabled: Boolean = false,
)

data class LeavePolicyRules(
    val sick: SickLeavePolicy = SickLeavePolicy(),
    val vacation: VacationLeavePolicy = VacationLeavePolicy(),
    /** Standard day used to convert between hours and days. Null means no reliable standard. */
    val standardDayMinutes: Int? = null,
    /** Unit the workplace's payslip states balances in. */
    val balanceUnit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
)

/**
 * Effective-dated like [CompensationProfile], for the same reason: editing a
 * policy today must not restate what an absence reported last month was
 * estimated to pay.
 */
data class LeavePolicy(
    val id: String,
    val userId: String,
    val workplaceId: String,
    val regionCode: RegionCode,
    val rules: LeavePolicyRules,
    val effectiveFrom: Instant = Instant.EPOCH,
    val effectiveUntil: Instant? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
)

/**
 * A period the user was absent, as they experienced it: one illness, one
 * holiday. User-level, not per employer.
 *
 * For sick leave that is load-bearing. The ordinal sick day is counted from the
 * illness, so if the first day the user would have worked at their second job
 * falls on day three of one continuous illness, it is day three there too.
 * Restarting the count per employer would replay the 0% and 50% opening rungs at
 * every job and understate what the user is owed.
 */
data class AbsenceEvent(
    val id: String,
    val userId: String,
    val type: AbsenceType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
) {
    /** Every calendar date the absence covers, including non-working days. */
    val dates: List<LocalDate>
        get() = buildList {
            var cursor = startDate
            while (!cursor.isAfter(endDate)) {
                add(cursor)
                cursor = cursor.plusDays(1)
            }
        }

    /**
     * 1-based day of the absence period. Calendar days, not worked days: the
     * ladder in [SickLeavePolicy.payTiers] counts from the first day of illness.
     */
    fun ordinalFor(date: LocalDate): Int =
        (date.toEpochDay() - startDate.toEpochDay() + 1).toInt()
}

/**
 * The workplace-level half of an absence: one row per date the user would
 * otherwise have worked at that job.
 *
 * [policySnapshot] and [calculationSnapshot] freeze how the estimate was
 * reached, the same way `shifts.compensationSnapshot` does. A historical
 * estimate stays reproducible after the wage or the policy changes, and is
 * never silently recomputed — the user asks for that explicitly.
 */
data class AbsenceAllocation(
    val id: String,
    val userId: String,
    val absenceEventId: String,
    val workplaceId: String,
    val affectedDate: LocalDate,
    /** A whole day is 1.0; a half day 0.5. In [unit]. */
    val entitlementUnits: Double,
    val unit: LeaveBalanceUnit,
    val expectedWorkMinutes: Int? = null,
    val policySnapshot: LeavePolicySnapshot? = null,
    val calculationSnapshot: LeaveCalculationSnapshot? = null,
    val estimatedGrossPay: Double = 0.0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
)

/** The policy as it stood when an allocation was calculated. */
data class LeavePolicySnapshot(
    val policyId: String?,
    val regionCode: RegionCode,
    val rules: LeavePolicyRules,
    val capturedAt: Instant,
)

/**
 * How one allocation's money was arrived at, kept so the app can explain an old
 * estimate rather than re-deriving it under today's settings.
 */
data class LeaveCalculationSnapshot(
    val calculatedAt: Instant,
    val absenceType: AbsenceType,
    val workplaceId: String,
    val payBasis: String,
    val currencyCode: String,
    /** Sick only: which day of the illness this was. */
    val sickDayOrdinal: Int? = null,
    val multiplier: Double = 1.0,
    val expectedWorkMinutes: Int? = null,
    /** The full expected day before [multiplier]. */
    val baseAmount: Double = 0.0,
    val estimatedGrossPay: Double = 0.0,
    val balanceUnitsUsed: Double = 1.0,
    val balanceUnit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
    /** Set when a historical-average basis was used, so the UI can show the period. */
    val averagePeriodStart: LocalDate? = null,
    val averagePeriodEnd: LocalDate? = null,
    val averageGrossIncluded: Double? = null,
    val averageDivisor: Double? = null,
    /**
     * True when the preceding three months were not usable and another period was
     * chosen. Surfaced in the UI: picking a different period silently would make
     * a number the user cannot reconcile with their payslip.
     */
    val usedFallbackAveragePeriod: Boolean = false,
    val manualOverride: LeaveManualOverride? = null,
)

data class LeaveManualOverride(
    val enabled: Boolean,
    val reason: String? = null,
)

/**
 * The balance a user read off a payslip, kept as history rather than as a
 * mutable column. Entering August's payslip must not erase July's: the whole
 * estimate is "the last official number, minus what has been reported since",
 * which needs the date that number was true.
 */
data class LeaveBalanceSnapshot(
    val id: String,
    val userId: String,
    val workplaceId: String,
    val balanceType: AbsenceType,
    val balance: Double,
    val unit: LeaveBalanceUnit,
    val asOfDate: LocalDate,
    val source: LeaveBalanceSource = LeaveBalanceSource.PAYSLIP,
    val label: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
)
