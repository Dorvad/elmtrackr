"use client";

import { useEffect } from "react";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";

/** Lazily migrate legacy user_settings into a compensation profile. */
export function CompensationBootstrap() {
  const { settings, loading } = useSettings();
  const { ensureMigrated } = useCompensationProfiles();

  useEffect(() => {
    if (!loading && settings?.onboarding_completed) {
      ensureMigrated(settings).catch(() => {});
    }
  }, [loading, settings, ensureMigrated]);

  return null;
}
