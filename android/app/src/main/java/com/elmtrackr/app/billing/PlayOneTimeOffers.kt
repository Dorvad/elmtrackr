package com.elmtrackr.app.billing

import com.android.billingclient.api.ProductDetails

/**
 * The offer this product is actually sold as, or null when Play sent nothing
 * with a price.
 *
 * Billing Library 9 keeps every offer on [ProductDetails.getOneTimePurchaseOfferDetailsList]
 * and makes the singular getter the first item of that same list. The first
 * item is whichever purchase option was created first — a pre-order, a rent —
 * not necessarily the buy. Selecting here is what both the price and the
 * purchase sheet have to do, or the button and the sheet charge for different
 * things.
 */
internal fun ProductDetails.buyOffer(): OneTimeBuyOffer? {
    val offers = oneTimePurchaseOfferDetailsList.orEmpty().map { offer ->
        OneTimeBuyOffer(
            formattedPrice = offer.formattedPrice.orEmpty(),
            priceAmountMicros = offer.priceAmountMicros,
            offerToken = offer.offerToken?.takeIf { it.isNotEmpty() },
            rental = offer.rentalDetails != null,
            preorder = offer.preorderDetails != null,
        )
    }
    return selectOneTimeBuyOffer(offers)
}
