const CURRENCY_LOCALE: Record<string, string> = {
  ILS: "he-IL",
  USD: "en-US",
  GBP: "en-GB",
  EUR: "de-DE",
  CAD: "en-CA",
  AUD: "en-AU",
  JPY: "ja-JP",
  CHF: "de-CH",
};

/**
 * Format a monetary amount using the profile currency.
 * Falls back to ILS for Israeli users or USD when currency is unknown.
 */
export function formatCurrency(
  amount: number,
  currencyCode?: string | null,
  locale?: string
): string {
  const code = currencyCode?.toUpperCase() || "USD";
  const resolvedLocale = locale ?? CURRENCY_LOCALE[code] ?? "en-US";

  try {
    return new Intl.NumberFormat(resolvedLocale, {
      style: "currency",
      currency: code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${code} ${amount.toFixed(2)}`;
  }
}

/** Pick a sensible default currency when none is stored. */
export function fallbackCurrencyCode(
  regionCode?: string | null,
  timezone?: string | null
): string {
  if (regionCode === "IL" || timezone === "Asia/Jerusalem") return "ILS";
  if (regionCode === "GB") return "GBP";
  if (regionCode === "EU") return "EUR";
  return "USD";
}
