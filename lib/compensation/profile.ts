import type {
  CompensationProfile,
  CompensationRules,
  CompensationSnapshot,
  RegionCode,
  Shift,
  UserSettings,
} from "@/types";
import { getPresetByRegion } from "@/lib/compensation/presets";
import { fallbackCurrencyCode } from "@/lib/compensation/currency";
import { DEFAULT_WEEKEND_DAYS } from "@/lib/shifts/weekend";

/** Resolved profile used for pay calculation (live or snapshot). */
export interface ResolvedCompensation {
  profile_id: string | null;
  profile_name: string;
  region_code: RegionCode;
  currency_code: string;
  timezone: string;
  base_hourly_rate: number | null;
  rules_json: CompensationRules;
  stacking_policy: "highest_only" | "additive";
  from_snapshot: boolean;
}

export function snapshotToResolved(snapshot: CompensationSnapshot): ResolvedCompensation {
  return {
    profile_id: snapshot.profile_id,
    profile_name: snapshot.profile_name,
    region_code: snapshot.region_code,
    currency_code: snapshot.currency_code,
    timezone: snapshot.timezone,
    base_hourly_rate: snapshot.base_hourly_rate,
    rules_json: snapshot.rules_json,
    stacking_policy: snapshot.stacking_policy,
    from_snapshot: true,
  };
}

export function profileToResolved(profile: CompensationProfile): ResolvedCompensation {
  return {
    profile_id: profile.id,
    profile_name: profile.name,
    region_code: profile.region_code,
    currency_code: profile.currency_code,
    timezone: profile.timezone,
    base_hourly_rate: profile.base_hourly_rate,
    rules_json: profile.rules_json,
    stacking_policy: profile.stacking_policy,
    from_snapshot: false,
  };
}

/** Build a virtual profile from legacy user_settings fields. */
export function legacySettingsToResolved(settings: UserSettings): ResolvedCompensation {
  const regionCode: RegionCode =
    settings.region_code ??
    (settings.timezone === "Asia/Jerusalem" ? "IL" : "CUSTOM");
  const preset = getPresetByRegion(regionCode);
  const rules: CompensationRules = {
    ...preset.rules,
    regular: {
      ...preset.rules.regular,
      dailyStandardMinutes: settings.daily_overtime_threshold_minutes,
      weeklyStandardMinutes: settings.weekly_overtime_threshold_minutes,
      weekendDays: settings.weekend_days,
    },
    weekend: {
      ...preset.rules.weekend,
      days: settings.weekend_days,
    },
  };

  return {
    profile_id: null,
    profile_name: "Main job",
    region_code: regionCode,
    currency_code:
      settings.currency_code ??
      fallbackCurrencyCode(regionCode, settings.timezone),
    timezone: settings.timezone,
    base_hourly_rate: settings.hourly_rate,
    rules_json: rules,
    stacking_policy: preset.stackingPolicy,
    from_snapshot: false,
  };
}

export function buildCompensationSnapshot(
  resolved: ResolvedCompensation
): CompensationSnapshot {
  return {
    profile_id: resolved.profile_id ?? "legacy",
    profile_name: resolved.profile_name,
    region_code: resolved.region_code,
    currency_code: resolved.currency_code,
    timezone: resolved.timezone,
    base_hourly_rate: resolved.base_hourly_rate,
    rules_json: resolved.rules_json,
    stacking_policy: resolved.stacking_policy,
    calculated_at: new Date().toISOString(),
  };
}

/**
 * Resolve compensation for a shift:
 * 1. compensation_snapshot_json
 * 2. compensation_profile_id
 * 3. default_compensation_profile_id
 * 4. legacy user_settings
 */
export function resolveShiftCompensation(
  shift: Shift,
  settings: UserSettings,
  profilesById: Map<string, CompensationProfile>
): ResolvedCompensation {
  if (shift.compensation_snapshot_json) {
    return snapshotToResolved(shift.compensation_snapshot_json);
  }

  const profileId =
    shift.compensation_profile_id ?? settings.default_compensation_profile_id;

  if (profileId) {
    const profile = profilesById.get(profileId);
    if (profile) return profileToResolved(profile);
  }

  return legacySettingsToResolved(settings);
}

/** Build insert payload for migrating existing users to a compensation profile. */
export function buildMigrationProfileFromSettings(
  userId: string,
  settings: UserSettings
): Omit<CompensationProfile, "id" | "created_at" | "updated_at"> {
  const resolved = legacySettingsToResolved(settings);
  return {
    user_id: userId,
    name: "Main job",
    region_code: resolved.region_code,
    currency_code: resolved.currency_code,
    timezone: resolved.timezone,
    base_hourly_rate: resolved.base_hourly_rate,
    rules_json: resolved.rules_json,
    stacking_policy: resolved.stacking_policy,
    effective_from: new Date().toISOString(),
    effective_until: null,
    is_default: true,
    is_archived: false,
  };
}

export function createProfileFromPreset(
  userId: string,
  regionCode: RegionCode,
  overrides?: {
    name?: string;
    currencyCode?: string;
    timezone?: string;
    baseHourlyRate?: number | null;
  }
): Omit<CompensationProfile, "id" | "created_at" | "updated_at"> {
  const preset = getPresetByRegion(regionCode);
  return {
    user_id: userId,
    name: overrides?.name ?? preset.profileName,
    region_code: regionCode,
    currency_code: overrides?.currencyCode ?? preset.currencyCode,
    timezone: overrides?.timezone ?? preset.timezone,
    base_hourly_rate: overrides?.baseHourlyRate ?? null,
    rules_json: preset.rules,
    stacking_policy: preset.stackingPolicy,
    effective_from: new Date().toISOString(),
    effective_until: null,
    is_default: true,
    is_archived: false,
  };
}

/** Sync legacy settings fields from a compensation profile (for backwards compat). */
export function profileToLegacySettingsUpdates(
  profile: CompensationProfile
): Partial<UserSettings> {
  return {
    hourly_rate: profile.base_hourly_rate,
    timezone: profile.timezone,
    region_code: profile.region_code,
    currency_code: profile.currency_code,
    daily_overtime_threshold_minutes:
      profile.rules_json.regular.dailyStandardMinutes,
    weekly_overtime_threshold_minutes:
      profile.rules_json.regular.weeklyStandardMinutes,
    weekend_days:
      profile.rules_json.regular.weekendDays ?? DEFAULT_WEEKEND_DAYS,
    default_compensation_profile_id: profile.id,
  };
}
