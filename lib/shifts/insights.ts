/**
 * Report insights — monthly quick-stats, weekly breakdown, and the
 * rotating "Insights of the Day" carousel (4 cards, changes daily).
 */

import type { Shift, UserSettings } from "@/types";
import { netMinutes } from "./duration";
import { isWeekendDate } from "./weekend";
import { calculateShiftPay } from "./payroll";

// ── Israeli / real-world context ──────────────────────────────
const AVG_WORKDAY_HRS   = 8.5;   // CBS Israel average
const AVG_WEEK_HRS      = 43;    // CBS Israel 2023
const AVG_MOVIE_HRS     = 2;     // typical film runtime
const AVG_BOOK_HRS      = 8;     // ~250 pages at 30 p/hr
const TEL_EILAT_HRS     = 3.5;   // Tel Aviv → Eilat by car
const TLV_JFK_HRS       = 12;    // El Al non-stop flight
const PIZZA_PRICE_ILS   = 65;    // avg Israeli pizza
const BREAKING_BAD_HRS  = 47;    // full series
const COFFEE_RITUAL_MIN = 20;    // make + drink one coffee
const AVG_GAMER_HRS     = 30;    // avg gamer/month

// ── Types ──────────────────────────────────────────────────────

export interface MonthInsights {
  weekendShiftCount: number;
  overtimeShiftCount: number;
  avgShiftMinutes: number;
  avgPayPerShift: number | null;
  longestShift: { shift: Shift; minutes: number } | null;
  highestEarningShift: { shift: Shift; amount: number } | null;
}

export interface WeekData {
  label: string;
  dayRange: string;
  minutes: number;
  overtimeMinutes: number;
  pay: number | null;
  prevMonthMinutes: number;
}

export type InsightColor =
  | "indigo" | "emerald" | "amber" | "violet" | "rose" | "sky";

export interface DailyInsight {
  icon: string;
  title: string;
  text: string;
  color: InsightColor;
}

// ── Monthly quick-stats ────────────────────────────────────────

export function buildMonthInsights(
  completedShifts: Shift[],
  settings: UserSettings
): MonthInsights {
  const weekendShiftCount = completedShifts.filter((s) => {
    const dateStr = new Date(s.start_time).toISOString().slice(0, 10);
    return s.is_special_day || isWeekendDate(dateStr, settings.weekend_days);
  }).length;

  const overtimeShiftCount = completedShifts.filter(
    (s) => (netMinutes(s) ?? 0) > settings.daily_overtime_threshold_minutes
  ).length;

  const avgShiftMinutes =
    completedShifts.length > 0
      ? completedShifts.reduce((sum, s) => sum + (netMinutes(s) ?? 0), 0) /
        completedShifts.length
      : 0;

  let totalPay = 0;
  let highestEarningShift: MonthInsights["highestEarningShift"] = null;
  let hasPay = false;

  for (const s of completedShifts) {
    const pay = calculateShiftPay(s, settings);
    if (pay) {
      hasPay = true;
      totalPay += pay.total_gross;
      if (!highestEarningShift || pay.total_gross > highestEarningShift.amount) {
        highestEarningShift = { shift: s, amount: pay.total_gross };
      }
    }
  }

  const avgPayPerShift =
    hasPay && completedShifts.length > 0 ? totalPay / completedShifts.length : null;

  let longestShift: MonthInsights["longestShift"] = null;
  for (const s of completedShifts) {
    const mins = netMinutes(s) ?? 0;
    if (!longestShift || mins > longestShift.minutes) {
      longestShift = { shift: s, minutes: mins };
    }
  }

  return {
    weekendShiftCount,
    overtimeShiftCount,
    avgShiftMinutes,
    avgPayPerShift,
    longestShift,
    highestEarningShift,
  };
}

// ── Weekly breakdown ───────────────────────────────────────────

/** Calendar-week bucket (0-3) by day-of-month: 1-7, 8-14, 15-21, 22+ */
function weekBucket(shift: Shift): 0 | 1 | 2 | 3 {
  const day = new Date(shift.start_time).getUTCDate();
  if (day <= 7)  return 0;
  if (day <= 14) return 1;
  if (day <= 21) return 2;
  return 3;
}

const WEEK_RANGES = ["1–7", "8–14", "15–21", "22+"];

// Hard cap: a single shift can't contribute more than 24 h to a week bucket.
// Guards against accidentally-long shifts corrupting the comparison display.
const MAX_SHIFT_MIN = 24 * 60;

export function buildWeeklyBreakdown(
  completedShifts: Shift[],
  prevCompletedShifts: Shift[],
  settings: UserSettings
): WeekData[] {
  const hasPay = !!settings.hourly_rate;

  const weeks: WeekData[] = [0, 1, 2, 3].map((i) => ({
    label: `Week ${i + 1}`,
    dayRange: WEEK_RANGES[i],
    minutes: 0,
    overtimeMinutes: 0,
    pay: hasPay ? 0 : null,
    prevMonthMinutes: 0,
  }));

  for (const shift of completedShifts) {
    const wi = weekBucket(shift);
    const mins = Math.min(netMinutes(shift) ?? 0, MAX_SHIFT_MIN);
    weeks[wi].minutes += mins;
    weeks[wi].overtimeMinutes += Math.max(
      0,
      mins - settings.daily_overtime_threshold_minutes
    );
    if (hasPay) {
      weeks[wi].pay! += calculateShiftPay(shift, settings)?.total_gross ?? 0;
    }
  }

  for (const shift of prevCompletedShifts) {
    const wi = weekBucket(shift);
    // Cap previous month contributions the same way to keep deltas sane
    weeks[wi].prevMonthMinutes += Math.min(
      netMinutes(shift) ?? 0,
      MAX_SHIFT_MIN
    );
  }

  return weeks;
}

// ── Insight of the Day (pool of 13, pick 4 daily) ─────────────

function buildPool(
  completedShifts: Shift[],
  settings: UserSettings,
  totalMinutes: number
): DailyInsight[] {
  if (completedShifts.length === 0) return [];

  const totalHours  = totalMinutes / 60;
  const shiftCount  = completedShifts.length;
  const avgWeekHrs  = totalHours / 4.33;
  const hasRate     = !!settings.hourly_rate;
  const totalGross  = hasRate
    ? completedShifts.reduce(
        (sum, s) => sum + (calculateShiftPay(s, settings)?.total_gross ?? 0),
        0
      )
    : 0;

  const isAboveAvg  = avgWeekHrs >= AVG_WEEK_HRS;
  const hasNight    = completedShifts.some((s) => {
    const h = new Date(s.start_time).getUTCHours();
    return h >= 20 || h < 6;
  });

  const pool: DailyInsight[] = [
    // 1 — Movies
    {
      icon: "🎬",
      title: "Movie Marathon",
      text: `Your ${Math.round(totalHours)}h this month equals ${Math.max(1, Math.floor(totalHours / AVG_MOVIE_HRS))} full movies — back to back, no bathroom breaks.`,
      color: "violet",
    },
    // 2 — Israeli national average
    {
      icon: "🇮🇱",
      title: "vs. Israeli Average",
      text: `You averaged ${avgWeekHrs.toFixed(1)}h/week. The Israeli CBS average is ${AVG_WEEK_HRS}h/week — you're ${isAboveAvg ? "above the curve. Respect." : "keeping a healthy pace."}`,
      color: "indigo",
    },
    // 3 — Books
    {
      icon: "📚",
      title: "Book Club",
      text: `${Math.round(totalHours)} hours of work = ${Math.max(1, Math.floor(totalHours / AVG_BOOK_HRS))} books cover to cover. Your to-read list doesn't stand a chance.`,
      color: "amber",
    },
    // 4 — Road trip
    {
      icon: "🚗",
      title: "Road Trip",
      text: `Your hours this month equal ${(totalHours / TEL_EILAT_HRS).toFixed(1)} drives from Tel Aviv to Eilat. Sand dunes and deadlines.`,
      color: "emerald",
    },
    // 5 — Coffee
    {
      icon: "☕",
      title: "Coffee Math",
      text: `At 20 minutes per coffee ritual, your ${Math.round(totalHours)}h of work required roughly ${Math.round((totalHours * 60) / COFFEE_RITUAL_MIN)} cups of coffee to power through.`,
      color: "amber",
    },
    // 6 — Beach days
    {
      icon: "🏖️",
      title: "Beach Days",
      text: `You clocked ${shiftCount} shift${shiftCount !== 1 ? "s" : ""} this month. That's ${shiftCount} potential days at the beach you traded for work. Worth it?`,
      color: "sky",
    },
    // 7 — Flights
    {
      icon: "✈️",
      title: "Airborne Hours",
      text: `${Math.round(totalHours)}h worked = ${(totalHours / TLV_JFK_HRS).toFixed(1)} non-stop flights from Tel Aviv to New York. Jet lag optional.`,
      color: "sky",
    },
    // 8 — Binge watch
    {
      icon: "📺",
      title: "Binge Watch",
      text: `${Math.round(totalHours)}h of work = ${Math.max(1, Math.floor(totalHours / BREAKING_BAD_HRS))}× through Breaking Bad (${BREAKING_BAD_HRS}h). Better call it a month.`,
      color: "violet",
    },
    // 9 — Standard workdays
    {
      icon: "📅",
      title: "Workday Counter",
      text: `${Math.round(totalHours)}h equals ${(totalHours / AVG_WORKDAY_HRS).toFixed(1)} standard Israeli workdays (${AVG_WORKDAY_HRS}h). ${totalHours > AVG_WORKDAY_HRS * 21.5 ? "You put in extra — take a break. ☕" : "Solid, consistent effort."}`,
      color: "indigo",
    },
    // 10 — Gamer vs worker
    {
      icon: "🎮",
      title: "Gamer vs. Worker",
      text: `The average person games ~${AVG_GAMER_HRS}h/month. You worked ${Math.round(totalHours)}h — ${totalHours > AVG_GAMER_HRS ? `${Math.round(totalHours / AVG_GAMER_HRS * 10) / 10}× more time grinding IRL.` : "still saving time for side quests."}`,
      color: "violet",
    },
    // 11 — Gym sessions
    {
      icon: "💪",
      title: "Gym Sessions",
      text: `${Math.round(totalHours)}h of work = ${Math.floor(totalHours)} one-hour gym sessions you could've had. The real workout? Your dedication.`,
      color: "emerald",
    },
    // 12 — Steps equivalent (30 min walk ≈ 4,000 steps)
    {
      icon: "🚶",
      title: "Step Counter",
      text: `If every work-hour were a 30-min walk, you'd have logged ${((totalHours * 2 * 4000) / 1000).toFixed(0)}k steps this month. That's a long stroll.`,
      color: "sky",
    },
    // 13 — Pizza (conditional on hourly rate)
    ...(hasRate && totalGross > 0
      ? [
          {
            icon: "🍕",
            title: "Pizza Power",
            text: `Your gross earnings this month could buy you ${Math.floor(totalGross / PIZZA_PRICE_ILS)} pizzas at ₪${PIZZA_PRICE_ILS} each. Dinner is sorted for months.`,
            color: "rose" as InsightColor,
          },
        ]
      : []),
    // 14 — Night owl (conditional)
    ...(hasNight
      ? [
          {
            icon: "🌙",
            title: "Night Owl",
            text: "You clocked some late-night shifts this month. While the city sleeps, you grind. Israel respects the hustle.",
            color: "indigo" as InsightColor,
          },
        ]
      : []),
  ];

  return pool;
}

/**
 * Returns 4 daily insights picked from the pool.
 * All 4 rotate together at UTC midnight — each one is distinct.
 */
export function getDailyInsights(
  completedShifts: Shift[],
  settings: UserSettings,
  totalMinutes: number
): DailyInsight[] {
  const fallback: DailyInsight = {
    icon: "✨",
    title: "Just getting started",
    text: "Log your first completed shifts to unlock daily insights.",
    color: "indigo",
  };

  const pool = buildPool(completedShifts, settings, totalMinutes);
  if (pool.length === 0) return [fallback];

  const dayIndex = Math.floor(Date.now() / 86_400_000);
  const count = Math.min(4, pool.length);
  const result: DailyInsight[] = [];
  for (let i = 0; i < count; i++) {
    result.push(pool[(dayIndex + i) % pool.length]);
  }
  return result;
}
