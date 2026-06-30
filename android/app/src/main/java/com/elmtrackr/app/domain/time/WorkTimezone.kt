package com.elmtrackr.app.domain.time

import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** User work timezone for reports, payroll, and overnight boundaries. */
object WorkTimezone {

    fun zoneId(timezone: String?): ZoneId =
        runCatching { ZoneId.of(timezone?.takeIf { it.isNotBlank() } ?: "UTC") }
            .getOrDefault(ZoneOffset.UTC)

    fun zoneFor(settings: UserSettings): ZoneId = zoneId(settings.timezone)

    fun zoneFor(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
    ): ZoneId {
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        return zoneId(resolved.timezone.ifBlank { settings.timezone })
    }

    fun zoneFor(resolved: ResolvedCompensation, settings: UserSettings): ZoneId =
        zoneId(resolved.timezone.ifBlank { settings.timezone })

    fun shiftLocalDate(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
    ): LocalDate = shift.startTime.atZone(zoneFor(shift, settings, profiles)).toLocalDate()

    fun shiftLocalDate(shift: Shift, zone: ZoneId): LocalDate =
        shift.startTime.atZone(zone).toLocalDate()

    fun monthRangeEpochMillis(year: Int, month: Int, zone: ZoneId): Pair<Long, Long> {
        val ym = YearMonth.of(year, month)
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun dayRangeEpochMillis(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun instantAtLocalTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()
}
