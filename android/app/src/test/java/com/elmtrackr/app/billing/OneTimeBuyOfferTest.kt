package com.elmtrackr.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTimeBuyOfferTest {

    @Test
    fun `a single buy offer is the price`() {
        val buy = offer("₪7.90", token = "buy-token")

        assertEquals(buy, selectOneTimeBuyOffer(listOf(buy)))
    }

    @Test
    fun `a pre-order with no price does not hide the buy offer behind it`() {
        val preorder = offer("", preorder = true, token = "preorder")
        val buy = offer("₪7.90", token = "buy-token")

        assertEquals(buy, selectOneTimeBuyOffer(listOf(preorder, buy)))
    }

    @Test
    fun `a rent offer does not win over the buy offer`() {
        val rent = offer("₪3.90", rental = true, token = "rent")
        val buy = offer("₪7.90", token = "buy-token")

        assertEquals(buy, selectOneTimeBuyOffer(listOf(rent, buy)))
    }

    @Test
    fun `a blank formatted price is not a price`() {
        assertNull(selectOneTimeBuyOffer(listOf(offer(""))))
        assertNull(selectOneTimeBuyOffer(listOf(offer("   "))))
    }

    @Test
    fun `no offers means no price`() {
        assertNull(selectOneTimeBuyOffer(emptyList()))
    }

    private fun offer(
        price: String,
        token: String? = null,
        rental: Boolean = false,
        preorder: Boolean = false,
    ) = OneTimeBuyOffer(
        formattedPrice = price,
        priceAmountMicros = 7_900_000L,
        offerToken = token,
        rental = rental,
        preorder = preorder,
    )
}
