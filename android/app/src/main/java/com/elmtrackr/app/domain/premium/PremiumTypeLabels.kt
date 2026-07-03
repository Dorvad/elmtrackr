package com.elmtrackr.app.domain.premium

import com.elmtrackr.app.domain.model.PremiumType

object PremiumTypeLabels {
    fun title(type: PremiumType): String = when (type) {
        PremiumType.HIGHEST_ONLY -> "Highest only"
        PremiumType.ADDITIVE -> "Additive"
        PremiumType.MULTIPLICATIVE -> "Multiplicative"
        PremiumType.BASE_PLUS_PREMIUM -> "Base-plus-premium"
        PremiumType.PREMIUM_IN_REGULAR_RATE -> "Premium-in-regular-rate"
        PremiumType.EXCLUDED_FROM_REGULAR_RATE -> "Excluded-from-regular-rate"
    }

    fun description(type: PremiumType): String = when (type) {
        PremiumType.HIGHEST_ONLY -> "Use only the highest applicable multiplier"
        PremiumType.ADDITIVE -> "Add premiums together"
        PremiumType.MULTIPLICATIVE -> "Apply one multiplier on top of another"
        PremiumType.BASE_PLUS_PREMIUM -> "Apply overtime on base, then add separate premium"
        PremiumType.PREMIUM_IN_REGULAR_RATE -> "Include shift differential in the base used for overtime"
        PremiumType.EXCLUDED_FROM_REGULAR_RATE ->
            "Keep reimbursement/gift/holiday pay out of overtime base where allowed"
    }
}
