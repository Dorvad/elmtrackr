package com.elmtrackr.app.domain.compensation

import androidx.annotation.StringRes
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.StackingPolicy

/** UI labels for [StackingPolicy], matching the wording used for premium profiles. */
object StackingPolicyLabels {
    @StringRes
    fun titleRes(policy: StackingPolicy): Int = when (policy) {
        StackingPolicy.HIGHEST_ONLY -> R.string.stacking_highest_title
        StackingPolicy.ADDITIVE -> R.string.stacking_additive_title
        StackingPolicy.MULTIPLICATIVE -> R.string.stacking_multiplicative_title
        StackingPolicy.BASE_PLUS_PREMIUM -> R.string.stacking_base_plus_title
        StackingPolicy.PREMIUM_IN_REGULAR_RATE -> R.string.stacking_premium_in_rr_title
        StackingPolicy.EXCLUDED_FROM_REGULAR_RATE -> R.string.stacking_excluded_rr_title
    }

    @StringRes
    fun helperRes(policy: StackingPolicy): Int = when (policy) {
        StackingPolicy.HIGHEST_ONLY -> R.string.stacking_highest_helper
        StackingPolicy.ADDITIVE -> R.string.stacking_additive_helper
        StackingPolicy.MULTIPLICATIVE -> R.string.stacking_multiplicative_helper
        StackingPolicy.BASE_PLUS_PREMIUM -> R.string.stacking_base_plus_helper
        StackingPolicy.PREMIUM_IN_REGULAR_RATE -> R.string.stacking_premium_in_rr_helper
        StackingPolicy.EXCLUDED_FROM_REGULAR_RATE -> R.string.stacking_excluded_rr_helper
    }
}
