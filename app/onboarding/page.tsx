"use client";

export const dynamic = "force-dynamic";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { OnboardingFlow } from "@/components/onboarding/OnboardingFlow";

function OnboardingContent() {
  const params = useSearchParams();
  const replay = params.get("replay") === "true";
  return <OnboardingFlow replay={replay} />;
}

export default function OnboardingPage() {
  return (
    <Suspense fallback={null}>
      <OnboardingContent />
    </Suspense>
  );
}
