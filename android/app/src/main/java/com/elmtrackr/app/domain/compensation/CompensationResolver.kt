package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.WeekendRules
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import java.time.Instant

object CompensationResolver {

    fun snapshotToResolved(snapshot: CompensationSnapshot): ResolvedCompensation = ResolvedCompensation(
        profileId = snapshot.profileId,
        profileName = snapshot.profileName,
        regionCode = snapshot.regionCode,
        currencyCode = snapshot.currencyCode,
        timezone = snapshot.timezone,
        baseHourlyRate = snapshot.baseHourlyRate,
        rules = snapshot.rules,
        stackingPolicy = snapshot.stackingPolicy,
        fromSnapshot = true,
    )

    fun profileToResolved(profile: CompensationProfile): ResolvedCompensation = ResolvedCompensation(
        profileId = profile.id,
        profileName = profile.name,
        regionCode = profile.regionCode,
        currencyCode = profile.currencyCode,
        timezone = profile.timezone,
        baseHourlyRate = profile.baseHourlyRate,
        rules = profile.rules,
        stackingPolicy = profile.stackingPolicy,
        fromSnapshot = false,
    )

    fun legacySettingsToResolved(settings: UserSettings): ResolvedCompensation {
        val regionCode = settings.regionCode ?: RegionCode.IL
        val preset = RegionPresets.forRegion(regionCode)
        val rules = preset.rules.copy(
            dailyStandardMinutes = settings.dailyOvertimeThresholdMinutes,
            weeklyStandardMinutes = settings.weeklyOvertimeThresholdMinutes,
            weekendDays = settings.weekendDays,
        )
        return ResolvedCompensation(
            profileId = null,
            profileName = "Main job",
            regionCode = regionCode,
            currencyCode = settings.currencyCode ?: settings.currency.name,
            timezone = settings.timezone,
            baseHourlyRate = settings.hourlyRate,
            rules = rules,
            stackingPolicy = preset.stackingPolicy,
            fromSnapshot = false,
        )
    }

    fun isWeekendShift(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
    ): Boolean {
        val resolved = resolveShiftCompensation(shift, settings, profiles)
        if (!resolved.rules.weekendEnabled) return false
        val dateStr = WorkTimezone.shiftLocalDate(shift, settings, profiles).toString()
        return WeekendRules.isWeekendDate(dateStr, resolved.rules.weekendDays)
    }

    fun resolveShiftCompensation(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
    ): ResolvedCompensation {
        val resolved = when {
            shift.compensationSnapshot != null -> snapshotToResolved(shift.compensationSnapshot)
            else -> {
                val profileId = shift.compensationProfileId ?: settings.defaultCompensationProfileId
                if (profileId != null) {
                    profiles.firstOrNull { it.id == profileId || it.remoteId == profileId }
                        ?.let { return profileToResolved(it) }
                }
                legacySettingsToResolved(settings)
            }
        }
        val taskRate = shift.taskHourlyRateSnapshot?.takeIf { it > 0 }
        return if (taskRate != null) resolved.copy(baseHourlyRate = taskRate) else resolved
    }

    fun buildSnapshot(resolved: ResolvedCompensation): CompensationSnapshot = CompensationSnapshot(
        profileId = resolved.profileId ?: "legacy",
        profileName = resolved.profileName,
        regionCode = resolved.regionCode,
        currencyCode = resolved.currencyCode,
        timezone = resolved.timezone,
        baseHourlyRate = resolved.baseHourlyRate,
        rules = resolved.rules,
        stackingPolicy = resolved.stackingPolicy,
        calculatedAt = Instant.now(),
    )

    fun buildMigrationProfile(userId: String, settings: UserSettings): CompensationProfile {
        val resolved = legacySettingsToResolved(settings)
        return CompensationProfile(
            id = "",
            userId = userId,
            name = "Main job",
            regionCode = resolved.regionCode,
            currencyCode = resolved.currencyCode,
            timezone = resolved.timezone,
            baseHourlyRate = resolved.baseHourlyRate,
            rules = resolved.rules,
            stackingPolicy = resolved.stackingPolicy,
            isDefault = true,
        )
    }

    fun createFromPreset(
        userId: String,
        regionCode: RegionCode,
        currencyCode: String? = null,
        timezone: String? = null,
        baseHourlyRate: Double? = null,
    ): CompensationProfile {
        val preset = RegionPresets.forRegion(regionCode)
        return CompensationProfile(
            id = "",
            userId = userId,
            name = preset.profileName,
            regionCode = regionCode,
            currencyCode = currencyCode ?: preset.currencyCode,
            timezone = timezone ?: preset.timezone,
            baseHourlyRate = baseHourlyRate,
            rules = preset.rules,
            stackingPolicy = preset.stackingPolicy,
            isDefault = true,
        )
    }

    fun profileToLegacySettingsUpdates(profile: CompensationProfile): UserSettings.Updates =
        UserSettings.Updates(
            hourlyRate = profile.baseHourlyRate,
            timezone = profile.timezone,
            regionCode = profile.regionCode,
            currencyCode = profile.currencyCode,
            defaultCompensationProfileId = profile.id,
            dailyOvertimeThresholdMinutes = profile.rules.dailyStandardMinutes,
            weeklyOvertimeThresholdMinutes = profile.rules.weeklyStandardMinutes,
            weekendDays = profile.rules.weekendDays,
        )
}

object CompensationCurrency {
    fun fallback(regionCode: RegionCode?, timezone: String?): String = when {
        regionCode == RegionCode.IL || timezone == "Asia/Jerusalem" -> "ILS"
        regionCode == RegionCode.GB -> "GBP"
        regionCode == RegionCode.EU -> "EUR"
        else -> "USD"
    }
}

const val COMPENSATION_DISCLAIMER =
    "ElmTrackr provides estimated compensation based on the rules you configure. Actual pay may vary based on taxes, deductions, employer policy, payroll practices, collective agreements, local labor laws, and other applicable rules. ElmTrackr does not provide legal, tax, payroll, accounting, or financial advice."

const val COMPENSATION_ESTIMATE_NOTE = "Estimated only. Actual pay may vary."

const val COMPENSATION_PROFILE_HELPER =
    "Compensation estimates are based on this profile. Actual pay may vary by taxes, deductions, employer policy, local regulations, and payroll rules."
