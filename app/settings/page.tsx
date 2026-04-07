"use client";

export const dynamic = "force-dynamic";

import { useState, FormEvent } from "react";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSpinner } from "@/components/ui/Spinner";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { createClient } from "@/lib/supabase/client";

// ISO weekday numbers: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
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

  // Local form state — initialised when settings load
  const [timezone, setTimezone] = useState("");
  const [dailyHours, setDailyHours] = useState("");
  const [weeklyHours, setWeeklyHours] = useState("");
  const [weekendDays, setWeekendDays] = useState<number[]>([]);
  const [initialised, setInitialised] = useState(false);

  // Populate form once settings arrive
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

      if (isNaN(dailyMins) || dailyMins <= 0)
        throw new Error("Daily overtime threshold must be a positive number.");
      if (isNaN(weeklyMins) || weeklyMins <= 0)
        throw new Error("Weekly overtime threshold must be a positive number.");

      await saveSettings({
        timezone: timezone.trim() || "UTC",
        daily_overtime_threshold_minutes: dailyMins,
        weekly_overtime_threshold_minutes: weeklyMins,
        weekend_days: weekendDays,
      });
      toast("Settings saved", "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save settings", "error");
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
      <div className="min-h-screen bg-gray-50 pb-24">
        <PageHeader title="Settings" />
        <PageSpinner />
        <BottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      <PageHeader title="Settings" />

      <div className="max-w-md mx-auto px-4 pt-4">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Overtime thresholds */}
          <Card>
            <h2 className="text-sm font-semibold text-gray-700 mb-3">
              Overtime Thresholds
            </h2>
            <div className="flex flex-col gap-3">
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
          </Card>

          {/* Weekend days */}
          <Card>
            <h2 className="text-sm font-semibold text-gray-700 mb-3">
              Weekend Days
            </h2>
            <p className="text-xs text-gray-500 mb-3">
              Select which days count as weekend. Default: Friday &amp; Saturday.
            </p>
            <div className="flex gap-2 flex-wrap">
              {WEEKDAYS.map(({ label, value }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => toggleWeekendDay(value)}
                  className={[
                    "rounded-xl px-3 py-2 text-sm font-semibold transition-colors",
                    weekendDays.includes(value)
                      ? "bg-blue-600 text-white"
                      : "bg-gray-100 text-gray-600 hover:bg-gray-200",
                  ].join(" ")}
                >
                  {label}
                </button>
              ))}
            </div>
          </Card>

          {/* Timezone */}
          <Card>
            <h2 className="text-sm font-semibold text-gray-700 mb-3">
              Timezone
            </h2>
            <Input
              label="Timezone (IANA format)"
              placeholder="e.g. Asia/Riyadh, America/New_York"
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
              hint="Used for display and reporting context"
            />
          </Card>

          <Button type="submit" loading={saving} fullWidth size="lg">
            Save Settings
          </Button>
        </form>

        {/* Sign out */}
        <div className="mt-6 border-t border-gray-200 pt-6">
          <Button
            type="button"
            variant="ghost"
            fullWidth
            onClick={handleSignOut}
            className="text-red-500 hover:text-red-600 hover:bg-red-50"
          >
            Sign Out
          </Button>
        </div>
      </div>

      <BottomNav />
    </div>
  );
}
