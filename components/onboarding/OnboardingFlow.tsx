"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import { useToast } from "@/components/ui/Toast";
import type { RegionCode } from "@/types";
import {
  CURRENCY_OPTIONS,
  REGION_PRESETS,
  TIMEZONE_OPTIONS,
} from "@/lib/compensation/presets";

interface FeatureSelection {
  travel_refunds: boolean;
  paid_projects: boolean;
  insights: boolean;
  clock_styles: boolean;
}

const INITIAL_FEATURES: FeatureSelection = {
  travel_refunds: false,
  paid_projects: false,
  insights: true,
  clock_styles: true,
};

const TOTAL = 5;

export function OnboardingFlow({ replay = false }: { replay?: boolean }) {
  const { settings, saveSettings } = useSettings();
  const { createFromPreset, defaultProfile, updateProfile } = useCompensationProfiles();
  const { toast } = useToast();
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [regionCode, setRegionCode] = useState<RegionCode>("IL");
  const [currencyCode, setCurrencyCode] = useState("ILS");
  const [timezone, setTimezone] = useState("Asia/Jerusalem");
  const [features, setFeatures] = useState<FeatureSelection>(() => ({
    travel_refunds: settings?.features_travel_refunds ?? INITIAL_FEATURES.travel_refunds,
    paid_projects: settings?.features_paid_projects ?? INITIAL_FEATURES.paid_projects,
    insights: settings?.features_insights ?? INITIAL_FEATURES.insights,
    clock_styles: true,
  }));
  const [saving, setSaving] = useState(false);

  function next() { setStep((s) => Math.min(s + 1, TOTAL)); }
  function prev() { setStep((s) => Math.max(s - 1, 1)); }

  function selectRegion(code: RegionCode) {
    const preset = REGION_PRESETS.find((p) => p.regionCode === code);
    if (preset) {
      setRegionCode(code);
      setCurrencyCode(preset.currencyCode);
      setTimezone(preset.timezone);
    }
  }

  async function saveRegionAndContinue(overrides?: {
    regionCode?: RegionCode;
    currencyCode?: string;
    timezone?: string;
  }) {
    const code = overrides?.regionCode ?? regionCode;
    const curr = overrides?.currencyCode ?? currencyCode;
    const tz = overrides?.timezone ?? timezone;
    const preset = REGION_PRESETS.find((p) => p.regionCode === code);

    setSaving(true);
    try {
      let profileId: string;
      if (replay && defaultProfile) {
        const updated = await updateProfile(defaultProfile.id, {
          region_code: code,
          currency_code: curr,
          timezone: tz,
          rules_json: preset?.rules ?? defaultProfile.rules_json,
        });
        profileId = updated.id;
      } else {
        const profile = await createFromPreset(code, {
          currencyCode: curr,
          timezone: tz,
        });
        profileId = profile.id;
      }
      await saveSettings({
        region_code: code,
        currency_code: curr,
        timezone: tz,
        default_compensation_profile_id: profileId,
      });
      next();
    } catch (err) {
      toast(
        err instanceof Error ? err.message : "Failed to save region settings",
        "error"
      );
    } finally {
      setSaving(false);
    }
  }

  async function finish() {
    setSaving(true);
    try {
      await saveSettings({
        features_travel_refunds: features.travel_refunds,
        features_paid_projects: false,
        features_insights: features.insights,
        features_clock_styles: true,
        clock_style: "classic",
        onboarding_completed: true,
        onboarding_completed_at: new Date().toISOString(),
      });
      router.replace("/");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-start py-10 px-4"
      style={{ background: "var(--color-surface, #f9fafb)" }}
    >
      <div className="w-full max-w-sm">
        {step > 1 && (
          <div className="mb-8">
            <div className="h-1 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-indigo-600 rounded-full transition-all duration-500"
                style={{ width: `${(step / TOTAL) * 100}%` }}
              />
            </div>
            <p className="text-xs text-gray-400 font-medium mt-1.5 text-right">
              {step} / {TOTAL}
            </p>
          </div>
        )}

        {step === 1 && <WelcomeScreen onNext={next} replay={replay} />}
        {step === 2 && (
          <RegionScreen
            regionCode={regionCode}
            currencyCode={currencyCode}
            timezone={timezone}
            onSelectRegion={selectRegion}
            onCurrencyChange={setCurrencyCode}
            onTimezoneChange={setTimezone}
            onContinue={saveRegionAndContinue}
            onManual={() => {
              const custom = REGION_PRESETS.find((p) => p.regionCode === "CUSTOM");
              saveRegionAndContinue({
                regionCode: "CUSTOM",
                currencyCode: custom?.currencyCode ?? "USD",
                timezone: custom?.timezone ?? "UTC",
              });
            }}
            onBack={prev}
            saving={saving}
          />
        )}
        {step === 3 && <HowItWorksScreen onNext={next} onBack={prev} />}
        {step === 4 && (
          <FeaturesScreen
            features={features}
            onChange={setFeatures}
            onNext={next}
            onBack={prev}
          />
        )}
        {step === 5 && (
          <DoneScreen onFinish={finish} saving={saving} />
        )}
      </div>
    </div>
  );
}

// ── Screen 1: Welcome ─────────────────────────────────────────────────────────

function WelcomeScreen({ onNext, replay }: { onNext: () => void; replay: boolean }) {
  return (
    <div className="flex flex-col items-center text-center animate-fade-in-up pt-8">
      <div className="h-24 w-24 rounded-3xl bg-gradient-to-br from-indigo-600 to-violet-700 flex items-center justify-center mb-6 shadow-lg shadow-indigo-500/30">
        <svg className="h-12 w-12 text-white" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <h1 className="text-3xl font-extrabold text-gray-900 tracking-tight mb-3">
        {replay ? "Feature Setup" : "Welcome to ElmTrackr"}
      </h1>
      <p className="text-base text-gray-500 mb-10 leading-relaxed">
        {replay
          ? "Update your region, features, and preferences."
          : "Clock in once. See hours, pay estimate, and overtime instantly."}
      </p>
      <button
        type="button"
        onClick={onNext}
        className="w-full rounded-2xl bg-indigo-600 text-white font-bold text-base py-4 shadow-lg shadow-indigo-500/25 hover:bg-indigo-700 active:scale-[0.98] transition-all"
      >
        {replay ? "Review Setup" : "Get Started"}
      </button>
    </div>
  );
}

// ── Screen 2: Region selection ────────────────────────────────────────────────

function RegionScreen({
  regionCode,
  currencyCode,
  timezone,
  onSelectRegion,
  onCurrencyChange,
  onTimezoneChange,
  onContinue,
  onManual,
  onBack,
  saving,
}: {
  regionCode: RegionCode;
  currencyCode: string;
  timezone: string;
  onSelectRegion: (code: RegionCode) => void;
  onCurrencyChange: (code: string) => void;
  onTimezoneChange: (tz: string) => void;
  onContinue: () => void;
  onManual: () => void;
  onBack: () => void;
  saving: boolean;
}) {
  return (
    <div className="animate-fade-in-up">
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-1">
        Choose your region
      </h2>
      <p className="text-sm text-gray-400 mb-5">
        We&apos;ll use this to suggest a starting compensation setup. You can edit all rules later.
      </p>

      <div className="flex flex-col gap-2 mb-5">
        {REGION_PRESETS.map((preset) => (
          <button
            key={preset.regionCode}
            type="button"
            onClick={() => onSelectRegion(preset.regionCode)}
            className={[
              "w-full text-left rounded-2xl border p-4 transition-all",
              regionCode === preset.regionCode
                ? "bg-indigo-50 border-indigo-300 shadow-sm"
                : "bg-white border-gray-100 hover:border-gray-200",
            ].join(" ")}
          >
            <p className="text-sm font-bold text-gray-900">{preset.label}</p>
            <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{preset.description}</p>
          </button>
        ))}
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-4 mb-5 flex flex-col gap-3">
        <div>
          <label className="text-xs font-semibold text-gray-500 mb-1 block">Currency</label>
          <select
            value={currencyCode}
            onChange={(e) => onCurrencyChange(e.target.value)}
            className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
          >
            {CURRENCY_OPTIONS.map((c) => (
              <option key={c.code} value={c.code}>{c.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs font-semibold text-gray-500 mb-1 block">Timezone</label>
          <select
            value={timezone}
            onChange={(e) => onTimezoneChange(e.target.value)}
            className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
          >
            {TIMEZONE_OPTIONS.map((tz) => (
              <option key={tz.value} value={tz.value}>{tz.label}</option>
            ))}
          </select>
        </div>
      </div>

      <button
        type="button"
        onClick={onContinue}
        disabled={saving}
        className="w-full rounded-2xl bg-indigo-600 text-white font-bold text-sm py-4 shadow-lg shadow-indigo-500/25 hover:bg-indigo-700 disabled:opacity-60 active:scale-[0.98] transition-all mb-3"
      >
        {saving ? "Setting up…" : "Continue"}
      </button>
      <button
        type="button"
        onClick={onManual}
        disabled={saving}
        className="w-full rounded-2xl bg-gray-100 text-gray-600 font-bold text-sm py-3 hover:bg-gray-200 transition-all mb-3"
      >
        Set up manually
      </button>
      <button
        type="button"
        onClick={onBack}
        className="w-full text-sm text-gray-400 font-medium py-2"
      >
        Back
      </button>
    </div>
  );
}

// ── Screen 3: How it works ────────────────────────────────────────────────────

function HowItWorksScreen({ onNext, onBack }: { onNext: () => void; onBack: () => void }) {
  const items = [
    {
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      color: "bg-indigo-100 text-indigo-600",
      title: "Clock In & Out",
      desc: "One tap to start or stop a shift from the home screen.",
    },
    {
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
        </svg>
      ),
      color: "bg-emerald-100 text-emerald-600",
      title: "Track Your Hours",
      desc: "Daily, weekly, and monthly reports with overtime breakdowns.",
    },
    {
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
      color: "bg-amber-100 text-amber-600",
      title: "Estimate Your Pay",
      desc: "Gross pay estimates based on your compensation profile — not legal payroll advice.",
    },
  ];

  return (
    <div className="animate-fade-in-up">
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-1">How it works</h2>
      <p className="text-sm text-gray-400 mb-5">Clock in once. See hours, pay estimate, and overtime instantly.</p>
      <div className="flex flex-col gap-3 mb-8">
        {items.map((item) => (
          <div
            key={item.title}
            className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 flex items-center gap-4"
          >
            <div className={`h-12 w-12 rounded-2xl flex items-center justify-center flex-shrink-0 ${item.color}`}>
              {item.icon}
            </div>
            <div>
              <p className="text-sm font-bold text-gray-900">{item.title}</p>
              <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>
      <NavRow onBack={onBack} onNext={onNext} />
    </div>
  );
}

// ── Screen 4: Feature selection ───────────────────────────────────────────────

function FeatureToggleCard({
  icon,
  iconColor,
  title,
  desc,
  enabled,
  onToggle,
}: {
  icon: React.ReactNode;
  iconColor: string;
  title: string;
  desc: string;
  enabled: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={[
        "w-full flex items-center gap-4 rounded-2xl border p-4 text-left transition-all duration-200 active:scale-[0.99]",
        enabled
          ? "bg-indigo-50 border-indigo-200 shadow-sm"
          : "bg-white border-gray-100 shadow-sm hover:border-gray-200",
      ].join(" ")}
    >
      <div className={`h-12 w-12 rounded-2xl flex items-center justify-center flex-shrink-0 ${iconColor}`}>
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-bold ${enabled ? "text-indigo-800" : "text-gray-900"}`}>{title}</p>
        <p className={`text-xs mt-0.5 leading-relaxed ${enabled ? "text-indigo-500" : "text-gray-400"}`}>{desc}</p>
      </div>
      <div
        className={[
          "h-6 w-11 rounded-full flex-shrink-0 transition-colors duration-200 relative",
          enabled ? "bg-indigo-600" : "bg-gray-200",
        ].join(" ")}
      >
        <span
          className={[
            "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200",
            enabled ? "translate-x-5" : "translate-x-0",
          ].join(" ")}
        />
      </div>
    </button>
  );
}

function FeaturesScreen({
  features,
  onChange,
  onNext,
  onBack,
}: {
  features: FeatureSelection;
  onChange: (f: FeatureSelection) => void;
  onNext: () => void;
  onBack: () => void;
}) {
  function toggle(key: keyof FeatureSelection) {
    onChange({ ...features, [key]: !features[key] });
  }

  const featureList = [
    {
      key: "travel_refunds" as const,
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 18.75a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 00-3.213-9.193 2.056 2.056 0 00-1.58-.86H14.25M16.5 18.75h-2.25m0-11.177v-.958c0-.568-.422-1.048-.987-1.106a48.554 48.554 0 00-10.026 0 1.106 1.106 0 00-.987 1.106v7.635m12-6.677v6.677m0 4.5v-4.5m0 0h-12" />
        </svg>
      ),
      iconColor: "bg-orange-100 text-orange-600",
      title: "Travel Refunds",
      desc: "Track transport reimbursements for late-night or holiday shifts.",
    },
    {
      key: "insights" as const,
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
        </svg>
      ),
      iconColor: "bg-emerald-100 text-emerald-600",
      title: "Insights & Analytics",
      desc: "Smart summaries, weekly trends, and daily coaching nudges.",
    },
  ];

  return (
    <div className="animate-fade-in-up">
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-1">What do you need?</h2>
      <p className="text-sm text-gray-400 mb-5">
        Enable the features that fit your workflow. You can change these later in Settings.
      </p>
      <div className="flex flex-col gap-3 mb-8">
        {featureList.map((f) => (
          <FeatureToggleCard
            key={f.key}
            icon={f.icon}
            iconColor={f.iconColor}
            title={f.title}
            desc={f.desc}
            enabled={features[f.key]}
            onToggle={() => toggle(f.key)}
          />
        ))}
      </div>
      <NavRow onBack={onBack} onNext={onNext} />
    </div>
  );
}

// ── Screen 5: Done ────────────────────────────────────────────────────────────

function DoneScreen({
  onFinish,
  saving,
}: {
  onFinish: () => void;
  saving: boolean;
}) {
  return (
    <div className="flex flex-col items-center text-center animate-fade-in-up pt-8">
      <div className="h-20 w-20 rounded-full bg-emerald-100 flex items-center justify-center mb-5">
        <svg className="h-10 w-10 text-emerald-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-2">You&apos;re all set!</h2>
      <p className="text-sm text-gray-500 mb-8 leading-relaxed">
        Clock in once. See hours, pay estimate, and overtime instantly.
      </p>

      <button
        type="button"
        onClick={onFinish}
        disabled={saving}
        className="w-full rounded-2xl bg-indigo-600 text-white font-bold text-base py-4 shadow-lg shadow-indigo-500/25 hover:bg-indigo-700 disabled:opacity-60 active:scale-[0.98] transition-all"
      >
        {saving ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
            Saving…
          </span>
        ) : (
          "Clock in on the home screen"
        )}
      </button>
      <p className="text-xs text-gray-400 mt-4">
        Customize your clock face later in Settings → Manage Features
      </p>
    </div>
  );
}

// ── Shared nav row ────────────────────────────────────────────────────────────

function NavRow({ onBack, onNext }: { onBack: () => void; onNext: () => void }) {
  return (
    <div className="flex gap-3">
      <button
        type="button"
        onClick={onBack}
        className="flex-shrink-0 rounded-2xl bg-gray-100 text-gray-600 font-bold text-sm px-5 py-4 hover:bg-gray-200 active:scale-[0.98] transition-all"
      >
        Back
      </button>
      <button
        type="button"
        onClick={onNext}
        className="flex-1 rounded-2xl bg-indigo-600 text-white font-bold text-sm py-4 shadow-lg shadow-indigo-500/25 hover:bg-indigo-700 active:scale-[0.98] transition-all"
      >
        Next
      </button>
    </div>
  );
}
