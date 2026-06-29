package com.elmtrackr.app.domain.model

import java.time.Instant

enum class RegionCode {
    IL, US, GB, EU, CUSTOM;

    companion object {
        /** Parse web/Room persisted values without crashing on unknown or lowercase codes. */
        fun fromPersisted(raw: String?): RegionCode {
            if (raw.isNullOrBlank()) return IL
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: IL
        }
    }
}

enum class StackingPolicy { HIGHEST_ONLY, ADDITIVE;

    companion object {
        fun fromPersisted(raw: String?): StackingPolicy {
            if (raw.isNullOrBlank()) return HIGHEST_ONLY
            return when (raw.trim().lowercase()) {
                "additive" -> ADDITIVE
                else -> HIGHEST_ONLY
            }
        }
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
    val weekendDays: List<Int> = listOf(5, 6),
    val paidBreaks: Boolean = false,
    val autoDeductBreakMinutes: Int? = null,
    val minimumShiftMinutes: Int? = null,
    val rounding: RoundingRules = RoundingRules(),
    val overtimeEnabled: Boolean = true,
    val dailyOvertimeTiers: List<OvertimeTier> = emptyList(),
    val weeklyOvertimeTiers: List<OvertimeTier> = emptyList(),
    val weekendEnabled: Boolean = true,
    val weekendMultiplier: Double = 1.5,
    val weekendStacking: StackingPolicy = StackingPolicy.HIGHEST_ONLY,
    val holidayEnabled: Boolean = true,
    val holidayManualSpecialDayEnabled: Boolean = true,
    val holidayMultiplier: Double = 1.5,
    val holidayTiers: List<OvertimeTier>? = null,
    val holidayStacking: StackingPolicy = StackingPolicy.HIGHEST_ONLY,
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
