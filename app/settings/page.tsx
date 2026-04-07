"use client";

export const dynamic = "force-dynamic";

import { useState, FormEvent } from "react";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { createClient } from "@/lib/supabase/client";

const WEEKDAYS = [
  { label: "Sun", value: 0 },
  { label: "Mon", value: 1 },
  { label: "Tue", value: 2 },
  { label: "Wed", value: 3 },
  { label: "Thu", value: 4 },
  { label: "Fri", value: 5 },
  { label: "Sat", value: 6 },
];

export default function SettingsPage() {
  const { settings, loading, saveSettings } = useSettings();
  const { toast } = useToast();
  const supabase = createClient();

  const [saving, setSaving] = useState(false);
  const [timezone, setTimezone] = useState("");
  const [dailyHours, setDailyHours] = useState("");
  const [weeklyHours, setWeeklyHours] = useState("");
  const [weekendDays, setWeekendDays] = useState<number[]>([]);
  const [initialised, setInitialised] = useState(false);

  if (settings && !initialised) {
    setTimezone(settings.timezone);
    setDailyHours(String(settings.daily_overtime_threshold_minutes / 60));
    setWeeklyHours(String(settings.weekly_overtime_threshold_minutes / 60));
    setWeekendDays(settings.weekend_days);
    setInitialised(true);
  }

  function toggleWeekendDay(day: number) {
    setWeekendDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const dailyMins = Math.round(parseFloat(dailyHours) * 60);
      const weeklyMins = Math.round(parseFloat(weeklyHours) * 60);
      if (isNaN(dailyMins) || dailyMins <= 0) throw new Error("Daily threshold must be a positive number.");
      if (isNaN(weeklyMins) || weeklyMins <= 0) throw new Error("Weekly threshold must be a positive number.");
      await saveSettings({
        timezone: timezone.trim() || "UTC",
        daily_overtime_threshold_minutes: dailyMins,
        weekly_overtime_threshold_minutes: weeklyMins,
        weekend_days: weekendDays,
      });
      toast("Settings saved", "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handleSignOut() {
    await supabase.auth.signOut();
    window.location.href = "/auth/login";
  }

  if (loading || !initialised) {
    return (
      <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
        <div className="px-4 pt-12 pb-4"><h1 className="text-2xl font-extrabold text-gray-900">Settings</h1></div>
        <PageSpinner />
        <BottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      <div className="px-4 pt-12 pb-4 animate-fade-in">
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Settings</h1>
      </div>

      <div className="max-w-md mx-auto px-4">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Overtime thresholds */}
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-1">
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">
              Overtime Thresholds
            </h2>
            <div className="flex flex-col gap-4">
              <Input
                label="Daily overtime after (hours)"
                type="number"
                min={0.5}
                max={24}
                step={0.5}
                value={dailyHours}
                onChange={(e) => setDailyHours(e.target.value)}
                hint="Overtime kicks in after this many hours in a single shift"
              />
              <Input
                label="Weekly overtime after (hours)"
                type="number"
                min={1}
                max={168}
                step={0.5}
                value={weeklyHours}
                onChange={(e) => setWeeklyHours(e.target.value)}
                hint="Overtime kicks in after this many total hours per week"
              />
            </div>
          </div>

          {/* Weekend days */}
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-2">
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-1">
              Weekend Days
            </h2>
            <p className="text-xs text-gray-400 mb-4">
              Select which days count as weekend. Default: Friday &amp; Saturday.
            </p>
            <div className="flex gap-2 flex-wrap">
              {WEEKDAYS.map(({ label, value }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => toggleWeekendDay(value)}
                  className={[
                    "rounded-xl px-3 py-2 text-sm font-bold transition-all duration-150",
                    weekendDays.includes(value)
                      ? "bg-indigo-600 text-white shadow-md shadow-indigo-500/25"
                      : "bg-gray-100 text-gray-500 hover:bg-gray-200",
                  ].join(" ")}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          {/* Timezone */}
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-3">
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">
              Timezone
            </h2>
            <Input
              label="IANA timezone"
              placeholder="e.g. Asia/Riyadh, America/New_York"
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
              hint="Used for display and reporting context"
            />
          </div>

          <Button type="submit" loading={saving} fullWidth size="lg" className="animate-fade-in-up stagger-4">
            Save Settings
          </Button>
        </form>

        <div className="mt-4 animate-fade-in-up stagger-5">
          <Button
            type="button"
            variant="ghost"
            fullWidth
            onClick={handleSignOut}
            className="text-red-400 hover:text-red-600 hover:bg-red-50"
          >
            Sign Out
          </Button>
        </div>
      </div>

      <BottomNav />
    </div>
  );
}
