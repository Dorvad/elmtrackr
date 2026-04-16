/**
 * Country → IANA timezone mapping.
 *
 * For countries with multiple timezones we pick the most-populated / capital
 * city timezone. This can be extended later to support region selection.
 */

export interface Country {
  /** ISO 3166-1 alpha-2 country code */
  code: string;
  name: string;
  /** IANA timezone — sensible default for multi-TZ countries */
  timezone: string;
}

export const COUNTRIES: Country[] = [
  // ── Middle East ──────────────────────────────────────────────────────────
  { code: "IL", name: "Israel",                       timezone: "Asia/Jerusalem"            },
  { code: "PS", name: "Palestine",                    timezone: "Asia/Gaza"                 },
  { code: "JO", name: "Jordan",                       timezone: "Asia/Amman"                },
  { code: "LB", name: "Lebanon",                      timezone: "Asia/Beirut"               },
  { code: "SY", name: "Syria",                        timezone: "Asia/Damascus"             },
  { code: "IQ", name: "Iraq",                         timezone: "Asia/Baghdad"              },
  { code: "IR", name: "Iran",                         timezone: "Asia/Tehran"               },
  { code: "SA", name: "Saudi Arabia",                 timezone: "Asia/Riyadh"               },
  { code: "AE", name: "United Arab Emirates",         timezone: "Asia/Dubai"                },
  { code: "QA", name: "Qatar",                        timezone: "Asia/Qatar"                },
  { code: "KW", name: "Kuwait",                       timezone: "Asia/Kuwait"               },
  { code: "BH", name: "Bahrain",                      timezone: "Asia/Bahrain"              },
  { code: "OM", name: "Oman",                         timezone: "Asia/Muscat"               },
  { code: "YE", name: "Yemen",                        timezone: "Asia/Aden"                 },
  { code: "TR", name: "Turkey",                       timezone: "Europe/Istanbul"           },

  // ── Europe ───────────────────────────────────────────────────────────────
  { code: "AT", name: "Austria",                      timezone: "Europe/Vienna"             },
  { code: "BE", name: "Belgium",                      timezone: "Europe/Brussels"           },
  { code: "BG", name: "Bulgaria",                     timezone: "Europe/Sofia"              },
  { code: "CH", name: "Switzerland",                  timezone: "Europe/Zurich"             },
  { code: "CZ", name: "Czech Republic",               timezone: "Europe/Prague"             },
  { code: "DE", name: "Germany",                      timezone: "Europe/Berlin"             },
  { code: "DK", name: "Denmark",                      timezone: "Europe/Copenhagen"         },
  { code: "ES", name: "Spain",                        timezone: "Europe/Madrid"             },
  { code: "FI", name: "Finland",                      timezone: "Europe/Helsinki"           },
  { code: "FR", name: "France",                       timezone: "Europe/Paris"              },
  { code: "GB", name: "United Kingdom",               timezone: "Europe/London"             },
  { code: "GR", name: "Greece",                       timezone: "Europe/Athens"             },
  { code: "HR", name: "Croatia",                      timezone: "Europe/Zagreb"             },
  { code: "HU", name: "Hungary",                      timezone: "Europe/Budapest"           },
  { code: "IE", name: "Ireland",                      timezone: "Europe/Dublin"             },
  { code: "IT", name: "Italy",                        timezone: "Europe/Rome"               },
  { code: "NL", name: "Netherlands",                  timezone: "Europe/Amsterdam"          },
  { code: "NO", name: "Norway",                       timezone: "Europe/Oslo"               },
  { code: "PL", name: "Poland",                       timezone: "Europe/Warsaw"             },
  { code: "PT", name: "Portugal",                     timezone: "Europe/Lisbon"             },
  { code: "RO", name: "Romania",                      timezone: "Europe/Bucharest"          },
  { code: "RU", name: "Russia",                       timezone: "Europe/Moscow"             },
  { code: "SE", name: "Sweden",                       timezone: "Europe/Stockholm"          },
  { code: "SK", name: "Slovakia",                     timezone: "Europe/Bratislava"         },
  { code: "UA", name: "Ukraine",                      timezone: "Europe/Kiev"               },

  // ── Americas ─────────────────────────────────────────────────────────────
  { code: "AR", name: "Argentina",                    timezone: "America/Argentina/Buenos_Aires" },
  { code: "BR", name: "Brazil",                       timezone: "America/Sao_Paulo"         },
  { code: "CA", name: "Canada",                       timezone: "America/Toronto"           },
  { code: "CL", name: "Chile",                        timezone: "America/Santiago"          },
  { code: "CO", name: "Colombia",                     timezone: "America/Bogota"            },
  { code: "MX", name: "Mexico",                       timezone: "America/Mexico_City"       },
  { code: "PE", name: "Peru",                         timezone: "America/Lima"              },
  { code: "US", name: "United States",                timezone: "America/New_York"          },
  { code: "VE", name: "Venezuela",                    timezone: "America/Caracas"           },

  // ── Africa ───────────────────────────────────────────────────────────────
  { code: "DZ", name: "Algeria",                      timezone: "Africa/Algiers"            },
  { code: "EG", name: "Egypt",                        timezone: "Africa/Cairo"              },
  { code: "ET", name: "Ethiopia",                     timezone: "Africa/Addis_Ababa"        },
  { code: "GH", name: "Ghana",                        timezone: "Africa/Accra"              },
  { code: "KE", name: "Kenya",                        timezone: "Africa/Nairobi"            },
  { code: "MA", name: "Morocco",                      timezone: "Africa/Casablanca"         },
  { code: "NG", name: "Nigeria",                      timezone: "Africa/Lagos"              },
  { code: "TN", name: "Tunisia",                      timezone: "Africa/Tunis"              },
  { code: "ZA", name: "South Africa",                 timezone: "Africa/Johannesburg"       },

  // ── Asia-Pacific ─────────────────────────────────────────────────────────
  { code: "AU", name: "Australia",                    timezone: "Australia/Sydney"          },
  { code: "BD", name: "Bangladesh",                   timezone: "Asia/Dhaka"                },
  { code: "CN", name: "China",                        timezone: "Asia/Shanghai"             },
  { code: "HK", name: "Hong Kong",                    timezone: "Asia/Hong_Kong"            },
  { code: "ID", name: "Indonesia",                    timezone: "Asia/Jakarta"              },
  { code: "IN", name: "India",                        timezone: "Asia/Kolkata"              },
  { code: "JP", name: "Japan",                        timezone: "Asia/Tokyo"                },
  { code: "KR", name: "South Korea",                  timezone: "Asia/Seoul"                },
  { code: "MY", name: "Malaysia",                     timezone: "Asia/Kuala_Lumpur"         },
  { code: "NZ", name: "New Zealand",                  timezone: "Pacific/Auckland"          },
  { code: "PH", name: "Philippines",                  timezone: "Asia/Manila"               },
  { code: "PK", name: "Pakistan",                     timezone: "Asia/Karachi"              },
  { code: "SG", name: "Singapore",                    timezone: "Asia/Singapore"            },
  { code: "TH", name: "Thailand",                     timezone: "Asia/Bangkok"              },
  { code: "TW", name: "Taiwan",                       timezone: "Asia/Taipei"               },
  { code: "VN", name: "Vietnam",                      timezone: "Asia/Ho_Chi_Minh"          },
];

/**
 * Find the best-matching country for a stored IANA timezone string.
 * Returns null if no country in the list uses that timezone.
 */
export function timezoneToCountry(timezone: string): Country | null {
  return COUNTRIES.find((c) => c.timezone === timezone) ?? null;
}

/**
 * Countries sorted: Israel first (primary market), then alphabetically by name.
 */
export const COUNTRIES_SORTED: Country[] = [
  COUNTRIES.find((c) => c.code === "IL")!,
  ...COUNTRIES.filter((c) => c.code !== "IL").sort((a, b) =>
    a.name.localeCompare(b.name)
  ),
];
