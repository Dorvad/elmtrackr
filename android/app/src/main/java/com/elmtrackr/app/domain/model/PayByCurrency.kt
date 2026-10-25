package com.elmtrackr.app.domain.model

/**
 * Pay-engine gross, kept in one bucket per currency.
 *
 * Each shift is priced in its own compensation profile's currency, and ElmTrackr has
 * no exchange rates, so a figure that adds two buckets together is not an amount of
 * anything — "5,000" out of 4,000 shekels and 1,000 dollars is neither. This type has
 * no way to produce one: a caller reads one currency at a time, or the only one.
 *
 * [com.elmtrackr.app.domain.money.MoneyByCurrency] is the same rule for stored money,
 * held as BigDecimal and rounded on the way in. This one holds the engine's Double
 * figures unrounded, so a single-currency week or task total is summed and rounded
 * exactly as it was when it was a plain Double, and renders identically.
 *
 * Zero buckets are dropped: a currency in which nothing was earned is not a figure
 * worth showing, and keeping it would turn a one-currency total into a "mixed" one.
 */
@JvmInline
value class PayByCurrency private constructor(private val amounts: Map<String, Double>) {

    /** Currency codes present, alphabetically, so every surface lists them in one order. */
    val currencies: List<String> get() = amounts.keys.sorted()

    val isEmpty: Boolean get() = amounts.isEmpty()

    val isNotEmpty: Boolean get() = amounts.isNotEmpty()

    /** More than one currency: there is no single total, and none may be shown. */
    val isMixed: Boolean get() = amounts.size > 1

    /** The amount in [currencyCode], or 0.0 when nothing was earned in it. */
    operator fun get(currencyCode: String): Double = amounts[currencyCode] ?: 0.0

    /** Present buckets in [currencies] order. */
    fun entries(): List<Pair<String, Double>> = currencies.map { it to amounts.getValue(it) }

    /** The only currency and its amount; null when there are none or several. */
    fun singleOrNull(): Pair<String, Double>? = amounts.entries.singleOrNull()?.toPair()

    operator fun plus(other: PayByCurrency): PayByCurrency = from(entries() + other.entries())

    override fun toString(): String = entries().joinToString(", ") { (code, amount) -> "$code=$amount" }

    companion object {
        val EMPTY = PayByCurrency(emptyMap())

        /** Sums [amounts] per currency, in the order given, and drops zero buckets. */
        fun from(amounts: Iterable<Pair<String, Double>>): PayByCurrency {
            val grouped = LinkedHashMap<String, Double>()
            amounts.forEach { (code, amount) -> grouped[code] = (grouped[code] ?: 0.0) + amount }
            return PayByCurrency(grouped.filterValues { it != 0.0 })
        }

        fun of(currencyCode: String, amount: Double): PayByCurrency = from(listOf(currencyCode to amount))

        /** Each shift's gross in that shift's own currency. */
        fun ofShifts(breakdowns: Iterable<ShiftPayBreakdown>): PayByCurrency =
            from(breakdowns.map { it.currencyCode to it.totalGross })
    }
}
