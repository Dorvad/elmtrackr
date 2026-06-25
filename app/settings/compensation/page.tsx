"use client";

import { useState, FormEvent } from "react";
import Link from "next/link";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import {
  COMPENSATION_DISCLAIMER,
  COMPENSATION_PROFILE_HELPER,
} from "@/lib/compensation/constants";
import {
  CURRENCY_OPTIONS,
  REGION_PRESETS,
  TIMEZONE_OPTIONS,
} from "@/lib/compensation/presets";
import { formatCurrency } from "@/lib/compensation/currency";
import type { CompensationRules, RegionCode, StackingPolicy } from "@/types";

const WEEKDAYS = [
  { label: "Sun", value: 0 },
  { label: "Mon", value: 1 },
  { label: "Tue", value: 2 },
  { label: "Wed", value: 3 },
  { label: "Thu", value: 4 },
  { label: "Fri", value: 5 },
  { label: "Sat", value: 6 },
];

export default function CompensationSettingsPage() {
  const { settings, loading: settingsLoading } = useSettings();
  const { defaultProfile, loading: profilesLoading, updateProfile } =
    useCompensationProfiles();
  const { toast } = useToast();

  const [saving, setSaving] = useState(false);
  const [name, setName] = useState("");
  const [regionCode, setRegionCode] = useState<RegionCode>("IL");
  const [currencyCode, setCurrencyCode] = useState("ILS");
  const [timezone, setTimezone] = useState("Asia/Jerusalem");
  const [hourlyRate, setHourlyRate] = useState("");
  const [stackingPolicy, setStackingPolicy] = useState<StackingPolicy>("highest_only");
  const [rules, setRules] = useState<CompensationRules | null>(null);
  const [initialised, setInitialised] = useState(false);

  if (defaultProfile && !initialised) {
    setName(defaultProfile.name);
    setRegionCode(defaultProfile.region_code);
    setCurrencyCode(defaultProfile.currency_code);
    setTimezone(defaultProfile.timezone);
    setHourlyRate(
      defaultProfile.base_hourly_rate != null
        ? String(defaultProfile.base_hourly_rate)
        : ""
    );
    setStackingPolicy(defaultProfile.stacking_policy);
    setRules(defaultProfile.rules_json);
    setInitialised(true);
  }

  function updateRules(updater: (r: CompensationRules) => CompensationRules) {
    setRules((prev) => (prev ? updater(prev) : prev));
  }

  function toggleWeekendDay(day: number) {
    updateRules((r) => {
      const days = r.regular.weekendDays.includes(day)
        ? r.regular.weekendDays.filter((d) => d !== day)
        : [...r.regular.weekendDays, day];
      return {
        ...r,
        regular: { ...r.regular, weekendDays: days },
        weekend: { ...r.weekend, days },
      };
    });
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!defaultProfile || !rules) return;
    setSaving(true);
    try {
      const parsedRate = parseFloat(hourlyRate);
      const rate =
        hourlyRate.trim() === ""
          ? null
          : isNaN(parsedRate) || parsedRate < 0
            ? null
            : parsedRate;

      await updateProfile(defaultProfile.id, {
        name: name.trim() || "Main job",
        region_code: regionCode,
        currency_code: currencyCode,
        timezone,
        base_hourly_rate: rate,
        stacking_policy: stackingPolicy,
        rules_json: rules,
      });
      toast("Compensation rules saved", "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save", "error");
    } finally {
      setSaving(false);
    }
  }

  const loading = settingsLoading || profilesLoading;

  if (loading || !initialised || !rules) {
    return (
      <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
        <div className="px-5 pt-12 pb-4">
          <h1 className="text-3xl font-bold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}>
            Compensation Rules
          </h1>
        </div>
        <PageSpinner />
        <BottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
      <div className="px-5 pt-12 pb-4 animate-fade-in flex items-center gap-3">
        <Link
          href="/settings"
          className="h-9 w-9 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center justify-center text-gray-400 hover:text-gray-600 transition-all"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </Link>
        <h1 className="text-2xl font-bold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}>
          Compensation Rules
        </h1>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4">
        <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
          <p className="text-xs text-gray-500 leading-relaxed">{COMPENSATION_PROFILE_HELPER}</p>
          <p className="text-xs text-gray-400 mt-2 leading-relaxed">{COMPENSATION_DISCLAIMER}</p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
            <h2 className="text-xs font-bold uppercase mb-4" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
              Profile
            </h2>
            <div className="flex flex-col gap-4">
              <Input label="Profile name" value={name} onChange={(e) => setName(e.target.value)} />
              <div>
                <label className="text-xs font-semibold text-gray-600 mb-1.5 block">Region preset</label>
                <select
                  value={regionCode}
                  onChange={(e) => setRegionCode(e.target.value as RegionCode)}
                  className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm"
                >
                  {REGION_PRESETS.map((p) => (
                    <option key={p.regionCode} value={p.regionCode}>{p.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-600 mb-1.5 block">Currency</label>
                <select
                  value={currencyCode}
                  onChange={(e) => setCurrencyCode(e.target.value)}
                  className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm"
                >
                  {CURRENCY_OPTIONS.map((c) => (
                    <option key={c.code} value={c.code}>{c.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-600 mb-1.5 block">Timezone</label>
                <select
                  value={timezone}
                  onChange={(e) => setTimezone(e.target.value)}
                  className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm"
                >
                  {TIMEZONE_OPTIONS.map((tz) => (
                    <option key={tz.value} value={tz.value}>{tz.label}</option>
                  ))}
                </select>
              </div>
              <Input
                label={`Base hourly rate (${currencyCode})`}
                type="number"
                min={0}
                step={0.01}
                placeholder="e.g. 45.00"
                value={hourlyRate}
                onChange={(e) => setHourlyRate(e.target.value)}
                hint={
                  hourlyRate
                    ? `Example: ${formatCurrency(parseFloat(hourlyRate) || 0, currencyCode)}/hr`
                    : "Leave blank to disable pay estimates"
                }
              />
            </div>
          </div>

          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
            <h2 className="text-xs font-bold uppercase mb-4" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
              Overtime
            </h2>
            <div className="flex flex-col gap-4">
              <Input
                label="Daily standard hours"
                type="number"
                min={0.5}
                max={24}
                step={0.5}
                value={String(rules.regular.dailyStandardMinutes / 60)}
                onChange={(e) =>
                  updateRules((r) => ({
                    ...r,
                    regular: {
                      ...r.regular,
                      dailyStandardMinutes: Math.round(parseFloat(e.target.value || "8") * 60),
                    },
                  }))
                }
              />
              <Input
                label="Weekly standard hours"
                type="number"
                min={1}
                max={168}
                step={0.5}
                value={String(rules.regular.weeklyStandardMinutes / 60)}
                onChange={(e) =>
                  updateRules((r) => ({
                    ...r,
                    regular: {
                      ...r.regular,
                      weeklyStandardMinutes: Math.round(parseFloat(e.target.value || "40") * 60),
                    },
                  }))
                }
              />
              {rules.overtime.dailyTiers.length > 0 && (
                <Input
                  label="Daily OT multiplier (first tier)"
                  type="number"
                  min={1}
                  max={3}
                  step={0.05}
                  value={String(rules.overtime.dailyTiers[0]?.multiplier ?? 1.5)}
                  onChange={(e) => {
                    const mult = parseFloat(e.target.value) || 1.5;
                    updateRules((r) => ({
                      ...r,
                      overtime: {
                        ...r.overtime,
                        dailyTiers: r.overtime.dailyTiers.map((t, i) =>
                          i === 0 ? { ...t, multiplier: mult } : t
                        ),
                      },
                    }));
                  }}
                />
              )}
            </div>
          </div>

          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
            <h2 className="text-xs font-bold uppercase mb-1" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
              Weekend Days
            </h2>
            <p className="text-xs text-gray-400 mb-4">Days that may trigger weekend premium rates.</p>
            <div className="flex gap-2 flex-wrap">
              {WEEKDAYS.map(({ label, value }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => toggleWeekendDay(value)}
                  className={[
                    "rounded-xl px-3 py-2 text-sm font-bold transition-all duration-150",
                    rules.regular.weekendDays.includes(value)
                      ? "bg-indigo-600 text-white shadow-md shadow-indigo-500/25"
                      : "bg-gray-100 text-gray-500 hover:bg-gray-200",
                  ].join(" ")}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="mt-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-gray-700">Weekend premium</p>
                <p className="text-xs text-gray-400">Apply multiplier on weekend days</p>
              </div>
              <button
                type="button"
                onClick={() =>
                  updateRules((r) => ({
                    ...r,
                    weekend: { ...r.weekend, enabled: !r.weekend.enabled },
                  }))
                }
                className={[
                  "h-6 w-11 rounded-full relative transition-colors",
                  rules.weekend.enabled ? "bg-indigo-600" : "bg-gray-200",
                ].join(" ")}
              >
                <span
                  className={[
                    "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform",
                    rules.weekend.enabled ? "translate-x-5" : "translate-x-0",
                  ].join(" ")}
                />
              </button>
            </div>
            {rules.weekend.enabled && (
              <Input
                label="Weekend multiplier"
                type="number"
                min={1}
                max={3}
                step={0.05}
                value={String(rules.weekend.multiplier)}
                onChange={(e) =>
                  updateRules((r) => ({
                    ...r,
                    weekend: {
                      ...r.weekend,
                      multiplier: parseFloat(e.target.value) || 1.5,
                    },
                  }))
                }
                className="mt-3"
              />
            )}
          </div>

          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
            <h2 className="text-xs font-bold uppercase mb-4" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
              Holiday & Night
            </h2>
            <Input
              label="Holiday / special day multiplier"
              type="number"
              min={1}
              max={3}
              step={0.05}
              value={String(rules.holiday.multiplier)}
              onChange={(e) =>
                updateRules((r) => ({
                  ...r,
                  holiday: {
                    ...r.holiday,
                    multiplier: parseFloat(e.target.value) || 1.5,
                  },
                }))
              }
            />
            <div className="mt-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-gray-700">Night shift rules</p>
                <p className="text-xs text-gray-400">
                  {rules.night.startTime} – {rules.night.endTime}
                </p>
              </div>
              <button
                type="button"
                onClick={() =>
                  updateRules((r) => ({
                    ...r,
                    night: { ...r.night, enabled: !r.night.enabled },
                  }))
                }
                className={[
                  "h-6 w-11 rounded-full relative transition-colors",
                  rules.night.enabled ? "bg-indigo-600" : "bg-gray-200",
                ].join(" ")}
              >
                <span
                  className={[
                    "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform",
                    rules.night.enabled ? "translate-x-5" : "translate-x-0",
                  ].join(" ")}
                />
              </button>
            </div>
            {rules.night.enabled && (
              <div className="mt-3 flex flex-col gap-3">
                <div className="grid grid-cols-2 gap-3">
                  <Input
                    label="Night start"
                    type="time"
                    value={rules.night.startTime}
                    onChange={(e) =>
                      updateRules((r) => ({
                        ...r,
                        night: { ...r.night, startTime: e.target.value },
                      }))
                    }
                  />
                  <Input
                    label="Night end"
                    type="time"
                    value={rules.night.endTime}
                    onChange={(e) =>
                      updateRules((r) => ({
                        ...r,
                        night: { ...r.night, endTime: e.target.value },
                      }))
                    }
                  />
                </div>
                <Input
                  label="Night multiplier"
                  type="number"
                  min={1}
                  max={3}
                  step={0.05}
                  value={String(rules.night.multiplier)}
                  onChange={(e) =>
                    updateRules((r) => ({
                      ...r,
                      night: {
                        ...r.night,
                        multiplier: parseFloat(e.target.value) || 1.25,
                      },
                    }))
                  }
                />
              </div>
            )}
          </div>

          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
            <h2 className="text-xs font-bold uppercase mb-4" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
              Stacking & Deductions
            </h2>
            <div>
              <label className="text-xs font-semibold text-gray-600 mb-1.5 block">Stacking mode</label>
              <select
                value={stackingPolicy}
                onChange={(e) => setStackingPolicy(e.target.value as StackingPolicy)}
                className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm"
              >
                <option value="highest_only">Highest only — use the highest applicable premium</option>
                <option value="additive">Additive — add premium deltas together</option>
              </select>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-gray-700">Estimated deductions</p>
                <p className="text-xs text-gray-400">Off by default — for rough net estimates only</p>
              </div>
              <button
                type="button"
                onClick={() =>
                  updateRules((r) => ({
                    ...r,
                    deductions: {
                      ...r.deductions,
                      enabled: !r.deductions.enabled,
                      mode: !r.deductions.enabled ? "percentage" : "none",
                    },
                  }))
                }
                className={[
                  "h-6 w-11 rounded-full relative transition-colors",
                  rules.deductions.enabled ? "bg-indigo-600" : "bg-gray-200",
                ].join(" ")}
              >
                <span
                  className={[
                    "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform",
                    rules.deductions.enabled ? "translate-x-5" : "translate-x-0",
                  ].join(" ")}
                />
              </button>
            </div>
            {rules.deductions.enabled && (
              <Input
                label="Deduction percentage"
                type="number"
                min={0}
                max={100}
                step={0.5}
                value={String(rules.deductions.percentage)}
                onChange={(e) =>
                  updateRules((r) => ({
                    ...r,
                    deductions: {
                      ...r.deductions,
                      percentage: parseFloat(e.target.value) || 0,
                    },
                  }))
                }
                className="mt-3"
              />
            )}
          </div>

          <Button type="submit" loading={saving} fullWidth size="lg">
            Save Compensation Rules
          </Button>
        </form>
      </div>

      <BottomNav />
    </div>
  );
}
