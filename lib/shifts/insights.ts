/**
 * Report insights — monthly quick-stats, weekly breakdown, and the
 * rotating "Insight of the Day" card.
 */

import type { Shift, UserSettings } from "@/types";
import { netMinutes } from "./duration";
import { isWeekendDate } from "./weekend";
import { calculateShiftPay } from "./payroll";

// ── Israeli work context ───────────────────────────────────────
const AVG_WORKDAY_HRS = 8.5;   // CBS Israel average
const AVG_WEEK_HRS = 43;       // CBS Israel 2023
const AVG_MOVIE_HRS = 2;       // typical film runtime
const AVG_BOOK_HRS = 8;        // ~250 pages at 30 pages/hr
const TEL_EILAT_HRS = 3.5;     // Tel Aviv → Eilat by car
const PIZZA_PRICE_ILS = 65;    // avg Israeli pizza

// ── Types ──────────────────────────────────────────────────────

export interface MonthInsights {
  weekendShiftCount: number;
  overtimeShiftCount: number;
  avgShiftMinutes: number;
  avgPayPerShift: number | null;   // null = no hourly_rate configured
  longestShift: { shift: Shift; minutes: number } | null;
  highestEarningShift: { shift: Shift; amount: number } | null;
}

export interface WeekData {
  label: string;         // "Week 1" – "Week 4"
  dayRange: string;      // "1–7", "8–14", etc.
  minutes: number;
  overtimeMinutes: number;
  pay: number | null;    // null = no rate
  prevMonthMinutes: number;
}

export type InsightColor = "indigo" | "emerald" | "amber" | "violet" | "rose" | "sky";

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
    hasPay && completedShifts.length > 0
      ? totalPay / completedShifts.length
      : null;

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

/** Which calendar-week bucket (1-4) does a shift fall in, by day-of-month? */
function weekBucket(shift: Shift): 0 | 1 | 2 | 3 {
  const day = new Date(shift.start_time).getUTCDate();
  if (day <= 7) return 0;
  if (day <= 14) return 1;
  if (day <= 21) return 2;
  return 3;
}

const WEEK_RANGES = ["1–7", "8–14", "15–21", "22+"];

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
    const mins = netMinutes(shift) ?? 0;
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
    weeks[wi].prevMonthMinutes += netMinutes(shift) ?? 0;
  }

  return weeks;
}

// ── Insight of the Day ─────────────────────────────────────────

export function getDailyInsight(
  completedShifts: Shift[],
  settings: UserSettings,
  totalMinutes: number
): DailyInsight {
  if (completedShifts.length === 0) {
    return {
      icon: "✨",
      title: "Just getting started",
      text: "Log your first completed shifts this month to unlock your daily insight.",
      color: "indigo",
    };
  }

  const totalHours = totalMinutes / 60;
  const shiftCount = completedShifts.length;
  // Approximate average weekly hours this month (÷ 4.33 weeks)
  const avgWeekHrs = totalHours / 4.33;

  const hasRate = !!settings.hourly_rate;
  const totalGross = hasRate
    ? completedShifts.reduce(
        (sum, s) => sum + (calculateShiftPay(s, settings)?.total_gross ?? 0),
        0
      )
    : 0;

  const hasNightShifts = completedShifts.some((s) => {
    const h = new Date(s.start_time).getUTCHours();
    return h >= 20 || h < 6;
  });

  const pool: DailyInsight[] = [
    {
      icon: "🎬",
      title: "Movie Marathon",
      text: `Your ${Math.round(totalHours)}h this month equals ${Math.floor(
        totalHours / AVG_MOVIE_HRS
      )} full movies — back to back. Popcorn not included.`,
      color: "violet",
    },
    {
      icon: "🇮🇱",
      title: "vs. Israeli Average",
      text: `You averaged ${avgWeekHrs.toFixed(1)}h/week this month. The Israeli average is ${AVG_WEEK_HRS}h — you're ${
        avgWeekHrs >= AVG_WEEK_HRS ? "above" : "below"
      } the national curve.`,
      color: "indigo",
    },
    {
      icon: "📚",
      title: "Book Club",
      text: `${Math.round(totalHours)} hours of work this month — enough time to read ${Math.max(
        1,
        Math.floor(totalHours / AVG_BOOK_HRS)
      )} books cover to cover.`,
      color: "amber",
    },
    {
      icon: "🚗",
      title: "Road Trip",
      text: `Your working hours this month equal ${(
        totalHours / TEL_EILAT_HRS
      ).toFixed(1)} drives from Tel Aviv to Eilat. Sun, sand, and deadlines.`,
      color: "emerald",
    },
    {
      icon: "📅",
      title: "Working Days",
      text: `${Math.round(totalHours)}h equals ${(
        totalHours / AVG_WORKDAY_HRS
      ).toFixed(1)} standard Israeli workdays (${AVG_WORKDAY_HRS}h each). ${
        totalHours / AVG_WORKDAY_HRS > 20
          ? "That's a lot — take a break! ☕"
          : "Keep it up!"
      }`,
      color: "sky",
    },
    {
      icon: "⚡",
      title: "Overtime Warrior",
      text: `You clocked ${shiftCount} shift${shiftCount !== 1 ? "s" : ""} this month across ${Math.round(totalHours)}h. The average Israeli works ${Math.round(AVG_WORKDAY_HRS * 21.5)}h/month — ${
        totalHours > AVG_WORKDAY_HRS * 21.5 ? "you're putting in extra!" : "you're keeping a healthy pace."
      }`,
      color: "indigo",
    },
    ...(hasRate
      ? [
          {
            icon: "🍕",
            title: "Pizza Power",
            text: `Your gross earnings this month could buy you ${Math.floor(
              totalGross / PIZZA_PRICE_ILS
            )} pizzas. Treat yourself — you earned it.`,
            color: "rose" as InsightColor,
          },
        ]
      : []),
    ...(hasNightShifts
      ? [
          {
            icon: "🌙",
            title: "Night Owl",
            text: `You worked late-night shifts this month. While others sleep, you grind — Israel respects the hustle.`,
            color: "violet" as InsightColor,
          },
        ]
      : []),
    {
      icon: "🏖️",
      title: "Beach Days",
      text: `${shiftCount} shifts this month. That's ${shiftCount} potential beach days you traded for work. Hopefully the pay is worth the tan you missed.`,
      color: "sky",
    },
  ];

  // Rotate daily — deterministic per UTC day
  const dayIndex = Math.floor(Date.now() / 86_400_000);
  return pool[dayIndex % pool.length];
}
