package com.elmtrackr.app.domain.model

enum class CurrencyCode(
    val symbol: String,
    val displayName: String,
    val fractionDigits: Int = 2,
) {
    ILS("₪", "Israeli New Shekel"),
    USD("$", "US Dollar"),
    EUR("€", "Euro"),
    GBP("£", "British Pound"),
    CAD("CA$", "Canadian Dollar"),
    AUD("A$", "Australian Dollar"),
    JPY("¥", "Japanese Yen", fractionDigits = 0),
    CHF("CHF", "Swiss Franc"),
    ;

    companion object {
        fun from(value: String?): CurrencyCode = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: ILS
    }
}
