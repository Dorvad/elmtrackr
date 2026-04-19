"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import type { ClockStyle } from "@/types";

function Toggle({ enabled, onToggle }: { enabled: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={[
        "h-6 w-11 rounded-full flex-shrink-0 transition-colors duration-200 relative focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1",
        enabled ? "bg-indigo-600" : "bg-gray-200",
      ].join(" ")}
      aria-checked={enabled}
      role="switch"
    >
      <span
        className={[
          "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200",
          enabled ? "translate-x-5" : "translate-x-0",
        ].join(" ")}
      />
    </button>
  );
}

const CLOCK_STYLES: { value: ClockStyle; label: string; desc: string }[] = [
  { value: "classic", label: "Classic", desc: "Progress ring with full stats" },
  { value: "minimal", label: "Minimal", desc: "Clean time display, no ring" },
  { value: "bold",    label: "Bold",    desc: "Extra-large time, no distractions" },
  { value: "focus",   label: "Focus",   desc: "Dark card, distraction-free" },
  { value: "night",   label: "Night",   desc: "Dark with cyan glow effect" },
  { value: "retro",   label: "Retro",   desc: "Amber terminal glow display" },
];

export default function FeaturesPage() {
  const { settings, loading, saveSettings } = useSettings();
  const { toast } = useToast();
  const router = useRouter();

  const [travelRefunds, setTravelRefunds] = useState<boolean | null>(null);
  const [paidProjects,  setPaidProjects]  = useState<boolean | null>(null);
  const [insights,      setInsights]      = useState<boolean | null>(null);
  const [clockStyles,   setClockStyles]   = useState<boolean | null>(null);
  const [clockStyle,    setClockStyle]    = useState<ClockStyle | null>(null);
  const [saving, setSaving] = useState(false);
  const [initialised, setInitialised] = useState(false);

  if (settings && !initialised) {
    setTravelRefunds(settings.features_travel_refunds);
    setPaidProjects(settings.features_paid_projects);
    setInsights(settings.features_insights);
    setClockStyles(settings.features_clock_styles);
    setClockStyle(settings.clock_style);
    setInitialised(true);
  }

  async function handleSave() {
    setSaving(true);
    try {
      await saveSettings({
        features_travel_refunds: travelRefunds ?? false,
        features_paid_projects:  paidProjects  ?? false,
        features_insights:       insights      ?? true,
        features_clock_styles:   clockStyles   ?? true,
        clock_style:             clockStyle    ?? "classic",
      });
      toast("Features saved", "success");
      router.push("/settings");
    } catch {
      toast("Failed to save", "error");
    } finally {
      setSaving(false);
    }
  }

  if (loading || !initialised) {
    return (
      <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
        <div className="px-4 pt-12 pb-4">
          <h1 className="text-2xl font-extrabold text-gray-900">Manage Features</h1>
        </div>
        <PageSpinner />
        <BottomNav />
      </div>
    );
  }

  const features = [
    {
      key: "travelRefunds" as const,
      icon: (
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 18.75a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 00-3.213-9.193 2.056 2.056 0 00-1.58-.86H14.25M16.5 18.75h-2.25m0-11.177v-.958c0-.568-.422-1.048-.987-1.106a48.554 48.554 0 00-10.026 0 1.106 1.106 0 00-.987 1.106v7.635m12-6.677v6.677m0 4.5v-4.5m0 0h-12" />
        </svg>
      ),
      iconColor: "bg-orange-100 text-orange-600",
      title: "Travel Refunds",
      desc: "Track transport reimbursements for late-night or holiday shifts.",
      value: travelRefunds ?? false,
      onChange: setTravelRefunds,
    },
    {
      key: "paidProjects" as const,
      icon: (
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
        </svg>
      ),
      iconColor: "bg-violet-100 text-violet-600",
      title: "Paid Projects",
      desc: "Organize shifts by project or client and track earnings per project.",
      value: paidProjects ?? false,
      onChange: setPaidProjects,
    },
    {
      key: "insights" as const,
      icon: (
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
        </svg>
      ),
      iconColor: "bg-emerald-100 text-emerald-600",
      title: "Insights & Analytics",
      desc: "Smart summaries, weekly trends, and daily coaching nudges.",
      value: insights ?? true,
      onChange: setInsights,
    },
    {
      key: "clockStyles" as const,
      icon: (
        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9.53 16.122a3 3 0 00-5.78 1.128 2.25 2.25 0 01-2.4 2.245 4.5 4.5 0 008.4-2.245c0-.399-.078-.78-.22-1.128zm0 0a15.998 15.998 0 003.388-1.62m-5.043-.025a15.994 15.994 0 011.622-3.395m3.42 3.42a15.995 15.995 0 004.764-4.648l3.876-5.814a1.151 1.151 0 00-1.597-1.597L14.146 6.32a15.996 15.996 0 00-4.649 4.763m3.42 3.42a6.776 6.776 0 00-3.42-3.42" />
        </svg>
      ),
      iconColor: "bg-sky-100 text-sky-600",
      title: "Clock Styles",
      desc: "Choose from 6 clock widget styles: Classic, Minimal, Bold, Focus, Night, and Retro.",
      value: clockStyles ?? true,
      onChange: setClockStyles,
    },
  ];

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      <div className="px-4 pt-12 pb-4 flex items-center gap-3 animate-fade-in">
        <button
          onClick={() => router.back()}
          className="h-9 w-9 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center justify-center text-gray-400 hover:text-gray-600 transition-all"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Manage Features</h1>
          <p className="text-xs text-gray-400 mt-0.5">Enable only what you need.</p>
        </div>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4 pb-24">
        {/* Feature toggles */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden animate-fade-in-up stagger-1">
          {features.map((f, i) => (
            <div
              key={f.key}
              className={[
                "flex items-center gap-4 px-4 py-4",
                i < features.length - 1 ? "border-b border-gray-50" : "",
              ].join(" ")}
            >
              <div className={`h-10 w-10 rounded-xl flex items-center justify-center flex-shrink-0 ${f.iconColor}`}>
                {f.icon}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-gray-900">{f.title}</p>
                <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{f.desc}</p>
              </div>
              <Toggle enabled={f.value} onToggle={() => f.onChange(!f.value)} />
            </div>
          ))}
        </div>

        {/* Clock style — shown when clock_styles is enabled */}
        {(clockStyles ?? false) && (
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-2">
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">
              Clock Style
            </h2>
            <div className="flex flex-col gap-2">
              {CLOCK_STYLES.map((style) => (
                <button
                  key={style.value}
                  type="button"
                  onClick={() => setClockStyle(style.value)}
                  className={[
                    "flex items-center gap-3 rounded-xl p-3 text-left transition-all duration-150 active:scale-[0.99]",
                    (clockStyle ?? "classic") === style.value
                      ? "bg-indigo-50 border border-indigo-200"
                      : "border border-transparent hover:bg-gray-50",
                  ].join(" ")}
                >
                  <div
                    className={[
                      "h-5 w-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-colors",
                      (clockStyle ?? "classic") === style.value
                        ? "border-indigo-600 bg-indigo-600"
                        : "border-gray-300",
                    ].join(" ")}
                  >
                    {(clockStyle ?? "classic") === style.value && (
                      <svg className="h-3 w-3 text-white" fill="none" stroke="currentColor" strokeWidth={3} viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                      </svg>
                    )}
                  </div>
                  <div className="flex-1">
                    <p className={`text-sm font-bold ${(clockStyle ?? "classic") === style.value ? "text-indigo-800" : "text-gray-800"}`}>
                      {style.label}
                    </p>
                    <p className={`text-xs ${(clockStyle ?? "classic") === style.value ? "text-indigo-500" : "text-gray-400"}`}>
                      {style.desc}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Sticky save bar */}
      <div className="fixed bottom-20 left-0 right-0 px-4 z-10">
        <div className="max-w-md mx-auto">
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="w-full rounded-2xl bg-indigo-600 text-white font-bold text-base py-4 shadow-lg shadow-indigo-500/30 hover:bg-indigo-700 disabled:opacity-60 active:scale-[0.98] transition-all"
          >
            {saving ? (
              <span className="flex items-center justify-center gap-2">
                <span className="h-4 w-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
                Saving…
              </span>
            ) : (
              "Save Features"
            )}
          </button>
        </div>
      </div>

      <BottomNav />
    </div>
  );
}
