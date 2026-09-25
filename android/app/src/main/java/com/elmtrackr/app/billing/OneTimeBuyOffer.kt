package com.elmtrackr.app.billing

/**
 * One offer Play attached to a one-time product, reduced to what the store
 * needs to price it and to open the purchase sheet.
 *
 * Play can hand back several offers for one product: a buy option, a rent
 * option, a pre-order, a discount. The first one is not necessarily the one
 * for sale. A pre-order or a rent sitting at the front of the list has no
 * price the shop can print, and reading it anyway is what leaves the Buy
 * button disabled on "רכישה" with no amount.
 */
internal data class OneTimeBuyOffer(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val offerToken: String?,
    val rental: Boolean = false,
    val preorder: Boolean = false,
)

/**
 * The offer the shop should sell.
 *
 * A straight buy with a price wins. Rent and pre-order are different products
 * from the one the card describes, so they are used only when nothing else
 * has a price — better a real amount than a disabled button. Blank prices are
 * not prices: Play's parser fills a missing `formattedPrice` with an empty
 * string, and an empty string is not null, so treating it as a price enables
 * a button that says nothing.
 */
internal fun selectOneTimeBuyOffer(offers: List<OneTimeBuyOffer>): OneTimeBuyOffer? {
    val priced = offers.filter { it.formattedPrice.isNotBlank() }
    return priced.firstOrNull { !it.rental && !it.preorder } ?: priced.firstOrNull()
}
