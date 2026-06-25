package com.elmtrackr.app.domain.model

/** A single pay bracket (e.g. "100% Regular", "125% Overtime"). */
data class PayBracket(
    val label: String,
    val minutes: Int,
    val rate: Double,
    val amount: Double,
)

/** Pay breakdown for a single completed shift. */
data class ShiftPayBreakdown(
    val brackets: List<PayBracket>,
    val totalGross: Double,
    val regularGross: Double = 0.0,
    val overtimeGross: Double = 0.0,
    val weekendGross: Double = 0.0,
    val holidayGross: Double = 0.0,
    val nightGross: Double = 0.0,
    val deductionsGross: Double = 0.0,
    val netGross: Double = totalGross,
    val isSpecial: Boolean,
    val profileId: String? = null,
    val profileName: String? = null,
    val currencyCode: String = "USD",
    val disclaimer: String = "",
)
