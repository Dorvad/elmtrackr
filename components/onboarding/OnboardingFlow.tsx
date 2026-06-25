"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import type { ClockStyle, RegionCode } from "@/types";
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

const TOTAL = 6;

export function OnboardingFlow({ replay = false }: { replay?: boolean }) {
  const { settings, saveSettings } = useSettings();
  const { createFromPreset } = useCompensationProfiles();
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [regionCode, setRegionCode] = useState<RegionCode>("IL");
  const [currencyCode, setCurrencyCode] = useState("ILS");
  const [timezone, setTimezone] = useState("Asia/Jerusalem");
  const [features, setFeatures] = useState<FeatureSelection>(() => ({
    travel_refunds: settings?.features_travel_refunds ?? INITIAL_FEATURES.travel_refunds,
    paid_projects: settings?.features_paid_projects ?? INITIAL_FEATURES.paid_projects,
    insights: settings?.features_insights ?? INITIAL_FEATURES.insights,
    clock_styles: settings?.features_clock_styles ?? INITIAL_FEATURES.clock_styles,
  }));
  const [clockStyle, setClockStyle] = useState<ClockStyle>(settings?.clock_style ?? "classic");
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

  async function saveRegionAndContinue() {
    setSaving(true);
    try {
      const profile = await createFromPreset(regionCode, {
        currencyCode,
        timezone,
      });
      await saveSettings({
        region_code: regionCode,
        currency_code: currencyCode,
        timezone,
        default_compensation_profile_id: profile.id,
      });
      next();
    } finally {
      setSaving(false);
    }
  }

  async function finish() {
    setSaving(true);
    try {
      await saveSettings({
        features_travel_refunds: features.travel_refunds,
        features_paid_projects: features.paid_projects,
        features_insights: features.insights,
        features_clock_styles: features.clock_styles,
        clock_style: clockStyle,
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
              selectRegion("CUSTOM");
              saveRegionAndContinue();
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
          <ClockStyleScreen
            selected={clockStyle}
            onChange={setClockStyle}
            onNext={next}
            onBack={prev}
          />
        )}
        {step === 6 && (
          <DoneScreen features={features} onFinish={finish} saving={saving} />
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
          ? "Review and update your features and preferences."
          : "Smart shift tracking for modern workers. Let's get you set up in under a minute."}
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
      <p className="text-sm text-gray-400 mb-5">Three things ElmTrackr does really well.</p>
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
      key: "paid_projects" as const,
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
        </svg>
      ),
      iconColor: "bg-violet-100 text-violet-600",
      title: "Paid Projects",
      desc: "Organize shifts by project or client and track earnings per project.",
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
    {
      key: "clock_styles" as const,
      icon: (
        <svg className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9.53 16.122a3 3 0 00-5.78 1.128 2.25 2.25 0 01-2.4 2.245 4.5 4.5 0 008.4-2.245c0-.399-.078-.78-.22-1.128zm0 0a15.998 15.998 0 003.388-1.62m-5.043-.025a15.994 15.994 0 011.622-3.395m3.42 3.42a15.995 15.995 0 004.764-4.648l3.876-5.814a1.151 1.151 0 00-1.597-1.597L14.146 6.32a15.996 15.996 0 00-4.649 4.763m3.42 3.42a6.776 6.776 0 00-3.42-3.42" />
        </svg>
      ),
      iconColor: "bg-sky-100 text-sky-600",
      title: "Clock Styles",
      desc: "Choose between Classic, Minimal, or Focus clock widget designs.",
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

// ── Screen 5: Clock Style ─────────────────────────────────────────────────────

const CLOCK_STYLE_OPTIONS: {
  value: ClockStyle;
  label: string;
  desc: string;
  preview: React.ReactNode;
}[] = [
  {
    value: "classic",
    label: "Classic",
    desc: "Progress ring with full stats",
    preview: (
      <div className="flex flex-col items-center gap-1.5 py-3">
        <div className="h-14 w-14 rounded-full border-4 border-indigo-500 flex items-center justify-center">
          <span className="text-[11px] font-bold text-indigo-600 tabular-nums">1:23</span>
        </div>
        <div className="flex gap-1 items-center">
          <div className="h-1 w-6 rounded bg-indigo-400" />
          <div className="h-1 w-5 rounded bg-gray-200" />
        </div>
      </div>
    ),
  },
  {
    value: "minimal",
    label: "Minimal",
    desc: "Clean time display, no ring",
    preview: (
      <div className="flex flex-col items-center gap-1 py-3">
        <span className="text-xl font-extrabold text-gray-800 tabular-nums">1:23</span>
        <span className="text-[9px] text-gray-400 font-semibold uppercase tracking-widest">elapsed</span>
      </div>
    ),
  },
  {
    value: "bold",
    label: "Bold",
    desc: "Extra-large time, no distractions",
    preview: (
      <div className="flex flex-col items-center gap-1 py-3">
        <span className="text-2xl font-black text-gray-900 tabular-nums tracking-tighter">1:23</span>
        <span className="text-[9px] text-gray-400 font-bold uppercase tracking-widest">elapsed</span>
      </div>
    ),
  },
  {
    value: "focus",
    label: "Focus",
    desc: "Dark card, distraction-free",
    preview: (
      <div className="flex flex-col items-center py-3">
        <div className="rounded-xl bg-indigo-950 px-4 py-2.5">
          <span className="text-base font-extrabold text-white tabular-nums">1:23</span>
        </div>
      </div>
    ),
  },
  {
    value: "night",
    label: "Night",
    desc: "Dark with cyan glow effect",
    preview: (
      <div className="flex flex-col items-center gap-1 py-3 bg-gray-950 rounded-lg mx-1">
        <span
          className="text-xl font-extrabold text-cyan-400 tabular-nums"
          style={{ textShadow: "0 0 12px rgba(34,211,238,0.6)" }}
        >
          1:23
        </span>
        <span className="text-[9px] text-cyan-800 font-bold uppercase tracking-widest">active</span>
      </div>
    ),
  },
  {
    value: "retro",
    label: "Retro",
    desc: "Amber terminal glow display",
    preview: (
      <div className="flex flex-col items-center py-3 bg-gray-950 rounded-lg mx-1">
        <div className="bg-gray-900 rounded-lg px-3 py-1.5">
          <span
            className="text-xl font-mono font-bold text-amber-400 tabular-nums tracking-widest"
            style={{ textShadow: "0 0 8px rgba(251,191,36,0.5)" }}
          >
            01:23
          </span>
        </div>
      </div>
    ),
  },
  {
    value: "aurora",
    label: "Aurora",
    desc: "Rainbow gradient progress ring",
    preview: (
      <div className="flex flex-col items-center py-3">
        <div className="relative" style={{ width: 56, height: 56 }}>
          <svg width={56} height={56} viewBox="0 0 56 56" className="absolute inset-0">
            <defs>
              <linearGradient id="onb-aurora" x1="0" y1="0" x2="56" y2="56" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#6366f1" />
                <stop offset="50%" stopColor="#a855f7" />
                <stop offset="100%" stopColor="#f97316" />
              </linearGradient>
            </defs>
            <circle cx={28} cy={28} r={24} strokeWidth={5} stroke="#f1f5f9" fill="none" />
            <circle cx={28} cy={28} r={24} strokeWidth={5} stroke="url(#onb-aurora)" fill="none"
              strokeLinecap="round" strokeDasharray={150.8} strokeDashoffset={37.7}
              transform="rotate(-90 28 28)" />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="text-[9px] font-bold" style={{ background: "linear-gradient(135deg,#6366f1,#ec4899)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>1:23</span>
          </div>
        </div>
      </div>
    ),
  },
  {
    value: "pulse",
    label: "Pulse",
    desc: "Glowing concentric rings on dark",
    preview: (
      <div className="flex flex-col items-center py-3 bg-gray-950 rounded-lg mx-1">
        <div className="relative" style={{ width: 52, height: 52 }}>
          <div className="absolute inset-0 rounded-full border-2 border-cyan-400" style={{ boxShadow: "0 0 8px rgba(34,211,238,0.5)" }} />
          <div className="absolute rounded-full border border-cyan-400" style={{ top: 8, left: 8, right: 8, bottom: 8, opacity: 0.6 }} />
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="text-[9px] font-bold text-cyan-400" style={{ textShadow: "0 0 8px rgba(34,211,238,0.6)" }}>1:23</span>
          </div>
        </div>
      </div>
    ),
  },
  {
    value: "dial",
    label: "Dial",
    desc: "Analog kitchen timer with sweep",
    preview: (
      <div className="flex items-center justify-center py-3">
        <svg width={52} height={52} viewBox="0 0 52 52">
          <circle cx={26} cy={26} r={22} fill="white" stroke="#e5e7eb" strokeWidth={1} />
          <path d="M 26 26 L 26 4 A 22 22 0 0 1 45 37 Z" fill="rgba(220,38,38,0.15)" />
          {Array.from({ length: 12 }, (_, i) => {
            const a = (i * 30 - 90) * Math.PI / 180;
            return (
              <line key={i}
                x1={26 + Math.cos(a) * 17} y1={26 + Math.sin(a) * 17}
                x2={26 + Math.cos(a) * 21} y2={26 + Math.sin(a) * 21}
                stroke="#374151" strokeWidth="1.5" strokeLinecap="round"
              />
            );
          })}
          <line x1={26} y1={26} x2={43} y2={35} stroke="#dc2626" strokeWidth={2} strokeLinecap="round" />
          <circle cx={26} cy={26} r={4} fill="#1f2937" />
        </svg>
      </div>
    ),
  },
];

function ClockStyleScreen({
  selected,
  onChange,
  onNext,
  onBack,
}: {
  selected: ClockStyle;
  onChange: (s: ClockStyle) => void;
  onNext: () => void;
  onBack: () => void;
}) {
  return (
    <div className="animate-fade-in-up">
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-1">Pick your style</h2>
      <p className="text-sm text-gray-400 mb-5">
        Choose how your clock widget looks on the home screen.
      </p>
      <div className="flex flex-col gap-3 mb-8">
        {CLOCK_STYLE_OPTIONS.map((style) => (
          <button
            key={style.value}
            type="button"
            onClick={() => onChange(style.value)}
            className={[
              "flex items-center gap-4 rounded-2xl border p-4 text-left transition-all duration-200 active:scale-[0.99]",
              selected === style.value
                ? "bg-indigo-50 border-indigo-300 shadow-sm"
                : "bg-white border-gray-100 shadow-sm hover:border-gray-200",
            ].join(" ")}
          >
            <div className="w-24 bg-gray-50 rounded-xl flex items-center justify-center flex-shrink-0 overflow-hidden">
              {style.preview}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-0.5">
                <p className={`text-sm font-bold ${selected === style.value ? "text-indigo-800" : "text-gray-900"}`}>
                  {style.label}
                </p>
                {selected === style.value && (
                  <span className="h-4 w-4 rounded-full bg-indigo-600 flex items-center justify-center">
                    <svg className="h-2.5 w-2.5 text-white" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                    </svg>
                  </span>
                )}
              </div>
              <p className={`text-xs ${selected === style.value ? "text-indigo-500" : "text-gray-400"}`}>
                {style.desc}
              </p>
            </div>
          </button>
        ))}
      </div>
      <NavRow onBack={onBack} onNext={onNext} />
    </div>
  );
}

// ── Screen 6: Done ────────────────────────────────────────────────────────────

function DoneScreen({
  features,
  onFinish,
  saving,
}: {
  features: FeatureSelection;
  onFinish: () => void;
  saving: boolean;
}) {
  const enabledFeatures = [
    features.travel_refunds && "Travel Refunds",
    features.paid_projects && "Paid Projects",
    features.insights && "Insights & Analytics",
    features.clock_styles && "Clock Styles",
  ].filter(Boolean) as string[];

  return (
    <div className="flex flex-col items-center text-center animate-fade-in-up pt-8">
      <div className="h-20 w-20 rounded-full bg-emerald-100 flex items-center justify-center mb-5">
        <svg className="h-10 w-10 text-emerald-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight mb-2">You&apos;re all set!</h2>
      <p className="text-sm text-gray-400 mb-6">
        ElmTrackr is ready to go. Here&apos;s what&apos;s enabled:
      </p>

      {enabledFeatures.length > 0 ? (
        <div className="w-full bg-white rounded-2xl border border-gray-100 shadow-sm p-4 mb-8 text-left">
          {enabledFeatures.map((f) => (
            <div key={f} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
              <span className="h-5 w-5 rounded-full bg-emerald-100 flex items-center justify-center flex-shrink-0">
                <svg className="h-3 w-3 text-emerald-600" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                </svg>
              </span>
              <span className="text-sm font-medium text-gray-700">{f}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="w-full bg-gray-50 rounded-2xl border border-gray-100 p-4 mb-8">
          <p className="text-sm text-gray-400">No extra features — keeping it simple.</p>
        </div>
      )}

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
          "Start Tracking"
        )}
      </button>
      <p className="text-xs text-gray-400 mt-4">
        Change features anytime in Settings → Manage Features
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
