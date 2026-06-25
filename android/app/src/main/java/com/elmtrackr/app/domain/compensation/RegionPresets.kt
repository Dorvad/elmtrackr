package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.StackingPolicy

data class RegionPreset(
    val regionCode: RegionCode,
    val label: String,
    val description: String,
    val currencyCode: String,
    val timezone: String,
    val profileName: String,
    val rules: CompensationRules,
    val stackingPolicy: StackingPolicy,
)

object RegionPresets {

    private val defaultRounding = RoundingRules()

    private val ilRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        weekendDays = listOf(5, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(
            OvertimeTier(480, 1.25),
            OvertimeTier(600, 1.5),
        ),
        weekendEnabled = true,
        weekendMultiplier = 1.5,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
        holidayTiers = listOf(
            OvertimeTier(0, 1.5),
            OvertimeTier(120, 1.75),
            OvertimeTier(240, 2.0),
        ),
    )

    private val usRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        weekendDays = listOf(0, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(OvertimeTier(480, 1.5)),
        weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.5)),
        weekendEnabled = false,
        weekendMultiplier = 1.0,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    private val gbRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2880,
        weekendDays = listOf(0, 6),
        overtimeEnabled = true,
        weeklyOvertimeTiers = listOf(OvertimeTier(2880, 1.5)),
        weekendEnabled = false,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    private val euRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        weekendDays = listOf(0, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(OvertimeTier(480, 1.25)),
        weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.25)),
        weekendEnabled = false,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    private val customRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        weekendDays = listOf(5, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(OvertimeTier(480, 1.5)),
        weekendEnabled = false,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    val all: List<RegionPreset> = listOf(
        RegionPreset(
            regionCode = RegionCode.IL,
            label = "Israel",
            description = "Suggested starting point for Israeli-style overtime and weekend/holiday tiers. You can edit the rules to match your workplace.",
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            profileName = "Main job",
            rules = ilRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.US,
            label = "United States",
            description = "Suggested starting point for US-style daily and weekly overtime. You can edit the rules to match your workplace.",
            currencyCode = "USD",
            timezone = "America/New_York",
            profileName = "Main job",
            rules = usRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.GB,
            label = "United Kingdom",
            description = "Suggested starting point for UK-style weekly overtime. You can edit the rules to match your workplace.",
            currencyCode = "GBP",
            timezone = "Europe/London",
            profileName = "Main job",
            rules = gbRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.EU,
            label = "European Union",
            description = "Suggested starting point for EU-style overtime defaults. You can edit the rules to match your workplace.",
            currencyCode = "EUR",
            timezone = "Europe/Berlin",
            profileName = "Main job",
            rules = euRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.CUSTOM,
            label = "Custom / Manual",
            description = "Start with a blank slate and configure all compensation rules yourself.",
            currencyCode = "USD",
            timezone = "UTC",
            profileName = "Main job",
            rules = customRules.copy(rounding = defaultRounding),
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
    )

    fun forRegion(code: RegionCode): RegionPreset =
        all.firstOrNull { it.regionCode == code } ?: all.last()

    val currencyOptions = listOf(
        "ILS" to "ILS — Israeli Shekel",
        "USD" to "USD — US Dollar",
        "GBP" to "GBP — British Pound",
        "EUR" to "EUR — Euro",
        "CAD" to "CAD — Canadian Dollar",
        "AUD" to "AUD — Australian Dollar",
        "CHF" to "CHF — Swiss Franc",
        "JPY" to "JPY — Japanese Yen",
    )

    val timezoneOptions = listOf(
        "Asia/Jerusalem",
        "America/New_York",
        "America/Chicago",
        "America/Denver",
        "America/Los_Angeles",
        "Europe/London",
        "Europe/Berlin",
        "Europe/Paris",
        "UTC",
    )
}
