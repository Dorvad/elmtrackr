package com.elmtrackr.app.domain.model

import java.time.Instant

enum class RegionCode {
    IL, US, US_CA, GB, EU, CUSTOM;

    companion object {
        /** Parse web/Room persisted values without crashing on unknown or lowercase codes. */
        fun fromPersisted(raw: String?): RegionCode {
            if (raw.isNullOrBlank()) return IL
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: IL
        }
    }
}

/**
 * How overlapping rate premiums combine. Mirrors [PremiumType] so compensation
 * rules offer the same stacking options as custom premium profiles. The two
 * regular-rate modes exist for wire compatibility with premium profiles and
 * combine like [HIGHEST_ONLY]; their overtime-base semantics only apply to
 * shift premiums.
 */
enum class StackingPolicy {
    HIGHEST_ONLY,
    ADDITIVE,
    MULTIPLICATIVE,
    BASE_PLUS_PREMIUM,
    PREMIUM_IN_REGULAR_RATE,
    EXCLUDED_FROM_REGULAR_RATE,
    ;

    companion object {
        fun fromPersisted(raw: String?): StackingPolicy {
            if (raw.isNullOrBlank()) return HIGHEST_ONLY
            return when (raw.trim().lowercase()) {
                "additive" -> ADDITIVE
                "multiplicative" -> MULTIPLICATIVE
                "base_plus_premium" -> BASE_PLUS_PREMIUM
                "premium_in_regular_rate" -> PREMIUM_IN_REGULAR_RATE
                "excluded_from_regular_rate" -> EXCLUDED_FROM_REGULAR_RATE
                else -> HIGHEST_ONLY
            }
        }

        /** Options offered in profile settings, aligned with [PremiumType.selectable]. */
        val selectable: List<StackingPolicy> = listOf(
            HIGHEST_ONLY,
            ADDITIVE,
            MULTIPLICATIVE,
            BASE_PLUS_PREMIUM,
        )
    }
}

data class OvertimeTier(val afterMinutes: Int, val multiplier: Double)

data class RoundingRules(
    val enabled: Boolean = false,
    val incrementMinutes: Int = 15,
    val direction: String = "nearest",
)

data class CompensationRules(
    val dailyStandardMinutes: Int = 480,
    val weeklyStandardMinutes: Int = 2400,
    /**
     * First day of the pay week as a JS day index (0=Sun … 6=Sat). Weekly
     * overtime thresholds accumulate from this day. Israel and most US
     * workweeks start Sunday; the legacy default is Monday (ISO).
     */
    val weekStartDay: Int = 1,
    val weekendDays: List<Int> = listOf(5, 6),
    val paidBreaks: Boolean = false,
    val autoDeductBreakMinutes: Int? = null,
    val minimumShiftMinutes: Int? = null,
    val rounding: RoundingRules = RoundingRules(),
    val overtimeEnabled: Boolean = true,
    val dailyOvertimeTiers: List<OvertimeTier> = emptyList(),
    val weeklyOvertimeTiers: List<OvertimeTier> = emptyList(),
    /**
     * Premium ladder for the 7th consecutive workday in a single pay week
     * (California-style: 1.5× for the first 8 h, 2× beyond). [seventhDayTiers]
     * use afterMinutes relative to the start of that day's shift.
     */
    val seventhDayEnabled: Boolean = false,
    val seventhDayTiers: List<OvertimeTier> = emptyList(),
    val weekendEnabled: Boolean = true,
    val weekendMultiplier: Double = 1.5,
    /** Local time when weekly rest begins on the pre-rest day (e.g. Friday 17:00 before Shabbat). */
    val weeklyRestStartTime: String? = null,
    /** Daily OT threshold on the calendar day before weekly rest (e.g. Friday day shift). */
    val dayBeforeRestDailyStandardMinutes: Int? = null,
    val weekendStacking: StackingPolicy = StackingPolicy.HIGHEST_ONLY,
    val holidayEnabled: Boolean = true,
    val holidayManualSpecialDayEnabled: Boolean = true,
    val holidayMultiplier: Double = 1.5,
    val holidayTiers: List<OvertimeTier>? = null,
    val holidayStacking: StackingPolicy = StackingPolicy.HIGHEST_ONLY,
    /** When set and the shift is predominantly at night, daily OT uses this threshold instead of [dailyStandardMinutes]. */
    val nightDailyStandardMinutes: Int? = null,
    val nightEnabled: Boolean = false,
    val nightStartTime: String = "22:00",
    val nightEndTime: String = "06:00",
    val nightMultiplier: Double = 1.25,
    val nightApplyTo: String = "minutes_inside_window",
    val nightStacking: StackingPolicy = StackingPolicy.HIGHEST_ONLY,
    val deductionsEnabled: Boolean = false,
    val deductionsMode: String = "none",
    val deductionsPercentage: Double = 0.0,
    val deductionsFixedAmount: Double = 0.0,
)

/**
 * True when these rules can actually produce *daily* overtime pay.
 *
 * A profile can carry a daily standard without a daily ladder to pay from: the US
 * federal preset is weekly-only by design ([dailyOvertimeTiers] is empty), and the
 * UK preset ships with overtime off entirely. Measuring minutes past the daily
 * standard for either invents overtime hours that no pay figure ever pays.
 *
 * Named here rather than spelled out at each site because the two copies had
 * already drifted: the month-level overtime total was gated on this pair of
 * conditions while `MonthlyReportBuilder.buildShiftBreakdown` was not — and that
 * breakdown feeds the shift-row overtime badge, the per-shift rows in Reports, the
 * CSV, the PDF and the per-task totals.
 */
val CompensationRules.paysDailyOvertime: Boolean
    get() = overtimeEnabled && dailyOvertimeTiers.isNotEmpty()

data class CompensationProfile(
    val id: String,
    val userId: String,
    val name: String,
    val regionCode: RegionCode,
    val currencyCode: String,
    val timezone: String,
    val baseHourlyRate: Double?,
    val rules: CompensationRules,
    val stackingPolicy: StackingPolicy,
    val effectiveFrom: Instant = Instant.EPOCH,
    val effectiveUntil: Instant? = null,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    /**
     * The job this profile pays for, when known. A profile remains the authority
     * on how worked time is paid; this only records which employer it belongs to,
     * so leave entitlement and balances survive a raise or a new profile.
     */
    val workplaceId: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
)

data class CompensationSnapshot(
    val profileId: String,
    val profileName: String,
    val regionCode: RegionCode,
    val currencyCode: String,
    val timezone: String,
    val baseHourlyRate: Double?,
    val rules: CompensationRules,
    val stackingPolicy: StackingPolicy,
    val calculatedAt: Instant,
)

data class ResolvedCompensation(
    val profileId: String?,
    val profileName: String,
    val regionCode: RegionCode,
    val currencyCode: String,
    val timezone: String,
    val baseHourlyRate: Double?,
    val rules: CompensationRules,
    val stackingPolicy: StackingPolicy,
    val fromSnapshot: Boolean = false,
)
