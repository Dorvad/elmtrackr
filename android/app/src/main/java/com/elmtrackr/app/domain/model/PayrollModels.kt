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
    val isSpecial: Boolean,    // true = weekend or special-day tiers applied
)
