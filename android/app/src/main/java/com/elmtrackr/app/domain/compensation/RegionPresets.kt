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

    // Israeli law triggers OT on the daily OR weekly threshold, whichever is reached first
    // (HIGHEST_ONLY stacking in PayrollCalculator). weekendDays = Fri+Sat off → 5-day week → 8.6 h/day.
    // Weekly 125%/150% breakpoints are a starting point — verify with payroll/HR.
    // Weekly-rest premium (150%) is separate from overtime — OT during rest is 175%/200%.
    private val ilRules = CompensationRules(
        dailyStandardMinutes = 516,
        weeklyStandardMinutes = 2520,
        // Israeli work week runs Sunday–Saturday; weekly OT accumulates from Sunday.
        weekStartDay = 0,
        weekendDays = listOf(5, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(
            OvertimeTier(516, 1.25),
            OvertimeTier(636, 1.5),
        ),
        weeklyOvertimeTiers = listOf(
            OvertimeTier(2520, 1.25),
            OvertimeTier(2640, 1.5),
        ),
        weekendEnabled = true,
        weekendMultiplier = 1.5,
        weeklyRestStartTime = "17:00",
        dayBeforeRestDailyStandardMinutes = 420,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
        nightDailyStandardMinutes = 420,
        nightEnabled = true,
        nightStartTime = "22:00",
        nightEndTime = "06:00",
        nightMultiplier = 1.0,
    )

    private val usFederalRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        // FLSA workweeks are employer-defined; Sunday–Saturday is the common default.
        weekStartDay = 0,
        weekendDays = listOf(0, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = emptyList(),
        weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.5)),
        weekendEnabled = false,
        weekendMultiplier = 1.0,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    private val usCaliforniaRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2400,
        weekStartDay = 0,
        weekendDays = listOf(0, 6),
        overtimeEnabled = true,
        dailyOvertimeTiers = listOf(
            OvertimeTier(480, 1.5),
            OvertimeTier(720, 2.0),
        ),
        weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.5)),
        // California Labor Code-style 7th consecutive workday: 1.5× first 8 h, 2× after.
        seventhDayEnabled = true,
        seventhDayTiers = listOf(
            OvertimeTier(0, 1.5),
            OvertimeTier(480, 2.0),
        ),
        weekendEnabled = false,
        weekendMultiplier = 1.0,
        holidayEnabled = true,
        holidayManualSpecialDayEnabled = true,
        holidayMultiplier = 1.5,
    )

    // UK law sets no statutory overtime or holiday premium — overtime pay is purely
    // contractual, and the 48 h/week figure is the Working Time Regulations average
    // cap on hours, not a pay trigger. Overtime therefore ships disabled; the weekly
    // tier is kept as a ready-made template for users whose contract does pay 1.5×.
    private val gbRules = CompensationRules(
        dailyStandardMinutes = 480,
        weeklyStandardMinutes = 2880,
        weekendDays = listOf(0, 6),
        overtimeEnabled = false,
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
            regionCode = RegionCode.US,
            label = "United States (Federal / FLSA)",
            description = "Weekly overtime only (1.5× after 40 h/week per the FLSA). No daily overtime — edit rules if your state requires daily OT. Holiday premium applies only to shifts you mark as special; it is not federally mandated.",
            currencyCode = "USD",
            timezone = "America/New_York",
            profileName = "Main job",
            rules = usFederalRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.US_CA,
            label = "United States (California)",
            description = "California-style daily OT (1.5× after 8 h, 2× after 12 h), weekly OT at 40 h, and 7th-consecutive-workday premiums (1.5× first 8 h, 2× after). Edit to match your workplace.",
            currencyCode = "USD",
            timezone = "America/Los_Angeles",
            profileName = "Main job",
            rules = usCaliforniaRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.GB,
            label = "United Kingdom",
            description = "UK law sets no statutory overtime or bank-holiday premium, so overtime starts disabled — pay follows your contract. A 1.5× after 48 h/week template is ready if you enable overtime; edit it to match your contract.",
            currencyCode = "GBP",
            timezone = "Europe/London",
            profileName = "Main job",
            rules = gbRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.EU,
            label = "European Union",
            description = "Illustrative defaults only — overtime law and premiums vary significantly by EU member state. Edit to match your country and contract.",
            currencyCode = "EUR",
            timezone = "Europe/Berlin",
            profileName = "Main job",
            rules = euRules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        ),
        RegionPreset(
            regionCode = RegionCode.IL,
            label = "Israel",
            description = "Suggested starting point for Israeli-style daily/weekly overtime and weekend/holiday tiers. Edit to match your workplace.",
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            profileName = "Main job",
            rules = ilRules,
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
