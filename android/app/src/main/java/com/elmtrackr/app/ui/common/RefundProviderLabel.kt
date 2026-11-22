package com.elmtrackr.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.RefundProvider

/**
 * The name a ride provider is shown under.
 *
 * Six screens and the refund PDF used to print the enum's own name, lower-cased and
 * capitalised, so "Taxi" and "Other" were English in Hebrew, Arabic and Russian. The
 * three operators are brand names and stay as they are written; the two generic
 * entries come from resources.
 */
@Composable
fun refundProviderLabel(provider: RefundProvider): String =
    brandName(provider) ?: stringResource(genericLabelRes(provider))

/** The same label from a [Context] already set to the app's language — for the PDF. */
fun refundProviderLabel(provider: RefundProvider, context: Context): String =
    brandName(provider) ?: context.getString(genericLabelRes(provider))

private fun brandName(provider: RefundProvider): String? = when (provider) {
    RefundProvider.LIME -> "Lime"
    RefundProvider.DOTT -> "Dott"
    RefundProvider.BIRD -> "Bird"
    RefundProvider.TAXI, RefundProvider.OTHER -> null
}

private fun genericLabelRes(provider: RefundProvider): Int = when (provider) {
    RefundProvider.TAXI -> R.string.refund_provider_taxi
    else -> R.string.refund_provider_other
}
