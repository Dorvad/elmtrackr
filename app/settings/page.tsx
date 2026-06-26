"use client";

import { useState, FormEvent } from "react";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import { useProfile } from "@/hooks/useProfile";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { CountrySelect } from "@/components/ui/CountrySelect";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { createClient } from "@/lib/supabase/client";
import { getPasswordResetRedirectUrl } from "@/lib/supabase/auth-redirects";

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
  const { defaultProfile, updateProfile: updateCompensationProfile } =
    useCompensationProfiles();
  const { profile, loading: profileLoading, updateProfile } = useProfile();
  const { toast } = useToast();
  const supabase = createClient();

  const [saving, setSaving] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [timezone, setTimezone] = useState("");
  const [dailyHours, setDailyHours] = useState("");
  const [weeklyHours, setWeeklyHours] = useState("");
  const [weekendDays, setWeekendDays] = useState<number[]>([]);
  const [hourlyRate, setHourlyRate] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [initialised, setInitialised] = useState(false);
  const [profileInitialised, setProfileInitialised] = useState(false);

  if (settings && !initialised) {
    setTimezone(settings.timezone);
    setDailyHours(String(settings.daily_overtime_threshold_minutes / 60));
    setWeeklyHours(String(settings.weekly_overtime_threshold_minutes / 60));
    setWeekendDays(settings.weekend_days);
    setHourlyRate(settings.hourly_rate != null ? String(settings.hourly_rate) : "");
    setInitialised(true);
  }

  if (!profileLoading && !profileInitialised) {
    setDisplayName(profile?.full_name ?? "");
    setProfileInitialised(true);
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
      const parsedRate = parseFloat(hourlyRate);
      const rate = hourlyRate.trim() === "" ? null : isNaN(parsedRate) || parsedRate < 0 ? null : parsedRate;

      const settingsPayload = {
        timezone: timezone.trim() || "UTC",
        daily_overtime_threshold_minutes: dailyMins,
        weekly_overtime_threshold_minutes: weeklyMins,
        weekend_days: weekendDays,
        hourly_rate: rate,
      };

      const saves: Promise<unknown>[] = [
        updateProfile({
          full_name: displayName.trim() || null,
        }),
      ];

      if (defaultProfile) {
        saves.push(
          updateCompensationProfile(defaultProfile.id, {
            timezone: settingsPayload.timezone,
            base_hourly_rate: rate,
            rules_json: {
              ...defaultProfile.rules_json,
              regular: {
                ...defaultProfile.rules_json.regular,
                dailyStandardMinutes: dailyMins,
                weeklyStandardMinutes: weeklyMins,
                weekendDays: weekendDays,
              },
              weekend: {
                ...defaultProfile.rules_json.weekend,
                days: weekendDays,
              },
            },
          })
        );
      } else {
        saves.push(saveSettings(settingsPayload));
      }

      await Promise.all(saves);
      toast("Settings saved", "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save", "error");
    } finally {
      setSaving(false);
    }
  }

  async function handlePasswordReset() {
    setResetting(true);
    try {
      const { data: userData } = await supabase.auth.getUser();
      const email = userData.user?.email;
      if (!email) throw new Error("Could not retrieve your email address.");
      const { error } = await supabase.auth.resetPasswordForEmail(email, {
        redirectTo: getPasswordResetRedirectUrl(),
      });
      if (error) throw new Error(error.message);
      toast(`Reset link sent to ${email}`, "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to send reset email", "error");
    } finally {
      setResetting(false);
    }
  }

  async function handleSignOut() {
    await supabase.auth.signOut();
    window.location.href = "/auth/login";
  }

  if (loading || profileLoading || !initialised || !profileInitialised) {
    return (
      <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
        <div className="px-5 pt-12 pb-4">
          <h1 className="text-3xl font-bold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}>Settings</h1>
        </div>
        <PageSpinner />
        <BottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
      <div className="px-5 pt-12 pb-4 animate-fade-in">
        <h1 className="text-3xl font-bold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.02em" }}>Settings</h1>
      </div>

      <div className="max-w-md mx-auto px-4">
        {/* Appearance */}
        <div className="rounded-3xl bg-white border border-white/80 au-card p-4 mb-4 animate-fade-in-up stagger-1">
          <h2 className="text-xs font-bold uppercase mb-3" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
            Appearance
          </h2>
          <ThemeToggle />
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Profile */}
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-1">
            <h2 className="text-xs font-bold uppercase mb-1">
              Profile
            </h2>
            <p className="text-xs text-gray-400 mb-4">
              Your name is used for the personal greeting on the home screen.
            </p>
            <Input
              label="Display name"
              placeholder="e.g. Avi"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              hint="First name or nickname — whatever you prefer"
            />
          </div>

          {/* Overtime thresholds */}
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-2">
            <h2 className="text-xs font-bold uppercase mb-4">
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
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-2">
            <h2 className="text-xs font-bold uppercase mb-1">
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

          {/* Compensation */}
          <div className="rounded-3xl bg-white border border-white/80 au-card overflow-hidden animate-fade-in-up stagger-3">
            <a
              href="/settings/compensation"
              className="flex items-center justify-between px-4 py-4 hover:bg-gray-50 transition-colors"
            >
              <div>
                <h2 className="text-xs font-bold uppercase mb-1">Compensation Rules</h2>
                <p className="text-xs text-gray-400">
                  Edit pay estimation rules, rates, and regional presets
                </p>
              </div>
              <svg className="h-4 w-4 text-gray-300 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
              </svg>
            </a>
          </div>

          {/* Payroll (legacy quick access) */}
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-3">
            <h2 className="text-xs font-bold uppercase mb-1">
              Base Rate
            </h2>
            <p className="text-xs text-gray-400 mb-4">
              Set your hourly base rate to see estimated gross pay. Also editable in Compensation Rules.
            </p>
            <Input
              label="Hourly base rate"
              type="number"
              min={0}
              step={0.01}
              placeholder="e.g. 45.00"
              value={hourlyRate}
              onChange={(e) => setHourlyRate(e.target.value)}
              hint="Used for estimated compensation based on your profile rules"
            />
          </div>

          {/* Location */}
          <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-4">
            <h2 className="text-xs font-bold uppercase mb-4">
              Location
            </h2>
            <CountrySelect
              timezone={timezone}
              onTimezoneChange={setTimezone}
              label="Country"
            />
          </div>

          <Button type="submit" loading={saving} fullWidth size="lg" className="animate-fade-in-up stagger-5">
            Save Settings
          </Button>
        </form>

        {/* Features */}
        <div className="rounded-3xl bg-white border border-white/80 au-card overflow-hidden mt-4 animate-fade-in-up stagger-5">
          <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-4 pt-4 mb-1">
            Features
          </h2>
          <p className="text-xs text-gray-400 px-4 mb-3">
            Enable or disable optional features to keep the app focused.
          </p>
          <a
            href="/settings/features"
            className="flex items-center justify-between px-4 py-3 hover:bg-gray-50 border-t border-gray-50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="h-8 w-8 rounded-xl bg-indigo-100 flex items-center justify-center">
                <svg className="h-4 w-4 text-indigo-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
              <span className="text-sm font-semibold text-gray-800">Manage Features</span>
            </div>
            <svg className="h-4 w-4 text-gray-300" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </a>
          <a
            href="/onboarding?replay=true"
            className="flex items-center justify-between px-4 py-3 hover:bg-gray-50 border-t border-gray-50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="h-8 w-8 rounded-xl bg-gray-100 flex items-center justify-center">
                <svg className="h-4 w-4 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
                </svg>
              </div>
              <span className="text-sm font-semibold text-gray-800">View onboarding again</span>
            </div>
            <svg className="h-4 w-4 text-gray-300" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </a>
        </div>

        {/* Security */}
        <div className="rounded-3xl bg-white border border-white/80 au-card p-4 mt-4 animate-fade-in-up stagger-5">
          <h2 className="text-xs font-bold uppercase mb-1">
            Security
          </h2>
          <p className="text-xs text-gray-400 mb-4">
            We'll email you a link to reset your password.
          </p>
          <Button
            type="button"
            variant="secondary"
            fullWidth
            loading={resetting}
            onClick={handlePasswordReset}
          >
            Send Password Reset Email
          </Button>
        </div>

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
