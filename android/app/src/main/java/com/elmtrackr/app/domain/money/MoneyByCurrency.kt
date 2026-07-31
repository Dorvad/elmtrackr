package com.elmtrackr.app.domain.money

/**
 * Amounts kept in separate buckets, one per currency.
 *
 * ElmTrackr has no exchange rates and invents none, so there is no such thing as
 * "the total" across currencies. Rather than leaving that to each caller to
 * remember, this type makes it structurally impossible to get wrong: you can only
 * ever read a per-currency figure out of it.
 *
 * Empty buckets are dropped, so a report shows only the currencies that actually
 * have money in them.
 */
@JvmInline
value class MoneyByCurrency private constructor(private val amounts: Map<String, Money>) {

    /** Currency codes present, alphabetically, so a report's order is stable. */
    val currencies: List<String> get() = amounts.keys.sorted()

    val isEmpty: Boolean get() = amounts.isEmpty()

    val isNotEmpty: Boolean get() = amounts.isNotEmpty()

    /** How many currencies are represented. More than one means never show a single total. */
    val size: Int get() = amounts.size

    /**
     * The total in [currencyCode], or zero in that currency when there is none.
     * Zero rather than null so a caller rendering a known currency does not have
     * to special-case an absent bucket.
     */
    operator fun get(currencyCode: String): Money =
        amounts[currencyCode] ?: Money.zero(currencyCode)

    /** Present buckets, in [currencies] order. */
    fun entries(): List<Money> = currencies.map { amounts.getValue(it) }

    /**
     * The single total, but only when exactly one currency is present.
     *
     * Null with two or more, which is what stops a screen from rendering an
     * unexplained combined figure: there is no honest single number to show.
     */
    fun singleOrNull(): Money? = amounts.values.singleOrNull()

    operator fun plus(other: MoneyByCurrency): MoneyByCurrency {
        val merged = amounts.toMutableMap()
        other.amounts.forEach { (code, amount) ->
            merged[code] = merged[code]?.plus(amount) ?: amount
        }
        return of(merged)
    }

    fun plus(amount: Money): MoneyByCurrency {
        val merged = amounts.toMutableMap()
        merged[amount.currencyCode] = merged[amount.currencyCode]?.plus(amount) ?: amount
        return of(merged)
    }

    /** Per-currency difference, floored at zero — for an outstanding balance. */
    fun minusFlooredAtZero(other: MoneyByCurrency): MoneyByCurrency {
        val result = mutableMapOf<String, Money>()
        (amounts.keys + other.amounts.keys).forEach { code ->
            val difference = this[code] - other[code]
            result[code] = difference.coerceAtLeastZero()
        }
        return of(result)
    }

    override fun toString(): String =
        currencies.joinToString(", ") { "$it=${amounts.getValue(it).amount.toPlainString()}" }

    companion object {
        val EMPTY = MoneyByCurrency(emptyMap())

        /** Drops zero buckets: a currency with nothing in it is not a fact worth showing. */
        private fun of(amounts: Map<String, Money>): MoneyByCurrency =
            MoneyByCurrency(amounts.filterValues { !it.isZero })

        fun from(amounts: Iterable<Money>): MoneyByCurrency {
            val grouped = mutableMapOf<String, Money>()
            amounts.forEach { amount ->
                grouped[amount.currencyCode] = grouped[amount.currencyCode]?.plus(amount) ?: amount
            }
            return of(grouped)
        }

        fun of(amount: Money): MoneyByCurrency = of(mapOf(amount.currencyCode to amount))
    }
}
