import type { UserSettings } from "@/types";

export interface FeatureFlags {
  travel_refunds: boolean;
  paid_projects: boolean;
  insights: boolean;
  clock_styles: boolean;
}

export const RECOMMENDED_FEATURES: FeatureFlags = {
  travel_refunds: false,
  paid_projects: false,
  insights: true,
  clock_styles: true,
};

export function getFeatureFlags(settings: UserSettings | null): FeatureFlags {
  if (!settings) return RECOMMENDED_FEATURES;
  return {
    travel_refunds: settings.features_travel_refunds,
    paid_projects: settings.features_paid_projects,
    insights: settings.features_insights,
    clock_styles: settings.features_clock_styles,
  };
}
