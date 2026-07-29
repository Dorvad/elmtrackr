package com.elmtrackr.app.ui.onboarding

import java.util.TimeZone
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode

data class OnboardingInput(
    val displayName: String = "",
    val regionCode: RegionCode = RegionCode.IL,
    val currencyCode: String = "ILS",
    val timezone: String = TimeZone.getDefault().id,
    val dailyOvertimeHours: Double = 8.0,
    val weeklyOvertimeHours: Double = 40.0,
    val weekendDays: List<Int> = listOf(5, 6),
    val hourlyRate: Double? = null,
    val currency: CurrencyCode = CurrencyCode.ILS,
    val featuresTravelRefunds: Boolean = false,
    val featuresPaidProjects: Boolean = false,
    val featuresInsights: Boolean = true,
    val featuresClockStyles: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    /**
     * Optional Paid Projects defaults, collected only when
     * [featuresPaidProjects] is true. All are safe to leave unset — a skipped
     * tax step stores 0 basis points, which reads as "tax off".
     */
    val projectsDefaultRegionCode: RegionCode? = null,
    val projectsDefaultCurrencyCode: String? = null,
    val projectsTaxLabel: String? = null,
    val projectsTaxRateBasisPoints: Int = 0,
    val projectsTaxInclusive: Boolean = false,
    val preserveExisting: Boolean = false,
    val enableAppLock: Boolean = false,
)
