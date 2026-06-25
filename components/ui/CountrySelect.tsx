"use client";

import { COUNTRIES_SORTED, timezoneToCountry } from "@/lib/settings/countries";

interface CountrySelectProps {
  /** The currently stored IANA timezone string (source of truth). */
  timezone: string;
  /** Called with the new IANA timezone when the user picks a country. */
  onTimezoneChange: (tz: string) => void;
  label?: string;
  hint?: string;
}

/**
 * Replaces the raw timezone text input with a friendly country picker.
 * Internally maps country → IANA timezone so the DB contract is unchanged.
 *
 * Backward-compatible: if the stored timezone doesn't match any country
 * (e.g. legacy "UTC"), the select shows a placeholder and keeps the old
 * value until the user picks a country.
 */
export function CountrySelect({
  timezone,
  onTimezoneChange,
  label = "Country",
  hint,
}: CountrySelectProps) {
  const matched = timezoneToCountry(timezone);
  // Use empty string as "no match / placeholder" sentinel
  const selectedCode = matched?.code ?? "";

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-xs font-bold text-gray-600 uppercase tracking-wide">
          {label}
        </label>
      )}

      {/* Native select — triggers OS picker on mobile (best mobile UX) */}
      <div className="relative">
        <select
          value={selectedCode}
          onChange={(e) => {
            const country = COUNTRIES_SORTED.find((c) => c.code === e.target.value);
            if (country) onTimezoneChange(country.timezone);
          }}
          className={[
            "w-full appearance-none rounded-xl border bg-white px-3 py-3 pr-9",
            "text-sm font-medium text-gray-800",
            "focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent",
            "transition-all",
            selectedCode === "" ? "border-gray-200 text-gray-400" : "border-gray-200",
          ].join(" ")}
        >
          {/* Placeholder shown only while no country is matched */}
          {selectedCode === "" && (
            <option value="" disabled>
              Select your country…
            </option>
          )}

          {COUNTRIES_SORTED.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </select>

        {/* Chevron */}
        <div className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>

      {/* Show the resolved IANA timezone so power users can verify */}
      <p className="text-xs text-gray-400">
        {hint ?? "Used for correct shift times, dates, and calculations."}
        {matched && (
          <span className="text-gray-300 ml-1">· {matched.timezone}</span>
        )}
      </p>
    </div>
  );
}
