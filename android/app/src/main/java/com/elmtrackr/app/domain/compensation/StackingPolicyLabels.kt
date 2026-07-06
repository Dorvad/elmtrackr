package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.StackingPolicy

/** UI labels for [StackingPolicy], matching the wording used for premium profiles. */
object StackingPolicyLabels {
    fun title(policy: StackingPolicy): String = when (policy) {
        StackingPolicy.HIGHEST_ONLY -> "Highest only"
        StackingPolicy.ADDITIVE -> "Additive"
        StackingPolicy.MULTIPLICATIVE -> "Multiplicative"
        StackingPolicy.BASE_PLUS_PREMIUM -> "Base-plus-premium"
        StackingPolicy.PREMIUM_IN_REGULAR_RATE -> "Premium-in-regular-rate"
        StackingPolicy.EXCLUDED_FROM_REGULAR_RATE -> "Excluded-from-regular-rate"
    }

    fun helper(policy: StackingPolicy): String = when (policy) {
        StackingPolicy.HIGHEST_ONLY ->
            "When multiple premiums apply (e.g. overtime and night), only the highest rate is used — premiums do not stack on top of each other."
        StackingPolicy.ADDITIVE ->
            "When multiple premiums apply, they are added on top of the current rate (e.g. 150% overtime plus 25% night = 175%)."
        StackingPolicy.MULTIPLICATIVE ->
            "When multiple premiums apply, one multiplier is applied on top of the other (e.g. 150% overtime × 125% night = 187.5%)."
        StackingPolicy.BASE_PLUS_PREMIUM ->
            "Overtime applies to the base rate, then the premium is added as a separate line on top."
        StackingPolicy.PREMIUM_IN_REGULAR_RATE ->
            "Uses the highest applicable rate here; the premium is included in the base used for overtime on shift premiums."
        StackingPolicy.EXCLUDED_FROM_REGULAR_RATE ->
            "Uses the highest applicable rate here; the premium stays out of the overtime base on shift premiums."
    }
}
