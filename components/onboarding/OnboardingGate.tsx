"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSettings } from "@/hooks/useSettings";

export function OnboardingGate({ children }: { children: React.ReactNode }) {
  const { settings, loading } = useSettings();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (loading) return;
    if (!settings) return;
    if (settings.onboarding_completed) return;
    if (pathname.startsWith("/onboarding") || pathname.startsWith("/auth")) return;
    router.replace("/onboarding");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settings, loading, pathname]);

  return <>{children}</>;
}
