/**
 * Report insights — monthly quick-stats, weekly breakdown, and the
 * rotating "Insights of the Day" carousel (4 cards, changes daily).
 */

import type { Shift, UserSettings } from "@/types";
import { netMinutes } from "./duration";
import { isWeekendDate } from "./weekend";
import { calculateShiftPay } from "./payroll";

// ── Israeli / real-world context ──────────────────────────────
const AVG_WORKDAY_HRS   = 8.5;    // CBS Israel average
const AVG_WEEK_HRS      = 43;     // CBS Israel 2023
const AVG_MONTH_HRS     = 182;    // ~21.5 days × 8.5h
const AVG_MOVIE_HRS     = 2;      // typical film runtime
const AVG_BOOK_HRS      = 8;      // ~250 pages at 30 p/hr
const TEL_EILAT_HRS     = 3.5;    // Tel Aviv → Eilat by car
const TLV_JFK_HRS       = 12;     // El Al non-stop flight
const PIZZA_PRICE_ILS   = 65;     // avg Israeli pizza (2024)
const BREAKING_BAD_HRS  = 47;     // full series watch-through
const COFFEE_RITUAL_MIN = 20;     // make + drink one coffee
const AVG_GAMER_HRS     = 30;     // avg gamer/month
const SNAIL_KMH         = 0.05;   // garden snail top speed
const ISS_ORBIT_MIN     = 92;     // minutes per ISS orbit
const EARTH_WALK_HRS    = 8015;   // 40,075 km ÷ 5 km/h on foot
const BEE_LIFETIME_HRS  = 540;    // worker bee's foraging lifetime
const MIN_WAGE_ILS      = 32.30;  // Israeli minimum wage 2024 (₪/hr)
const DEGREE_HRS        = 4000;   // typical university degree study hours
const TITANIC_HRS       = 3_000_000; // man-hours to build the Titanic
const HATIKVA_MIN       = 1.33;   // Israeli national anthem duration
const DRUM_RECORD_HRS   = 739;    // world record longest drum solo
const SHARK_KMH         = 56;     // great white shark top speed
const WIKIPEDIA_HRS     = 4;      // avg hours to write one article
const SURF_HRS          = 1.5;    // average surf session
const TACO_MIN          = 10;     // minutes to make one taco
const SHWARMA_MIN       = 5;      // minutes to eat one shwarma
const TLAV_SUNSHINE_HRS = 3400;   // Tel Aviv annual sunshine hours
const ARMY_SERVICE_HRS  = 4600;   // Israeli male army service (~32 months)
const SHAKESPEARE_HRS   = 10_000; // estimated hours to write all his works
const CAROUSEL_MIN      = 3;      // carousel ride duration
const MONOPOLY_HRS      = 3;      // average Monopoly game
const HAIRCUT_MIN       = 30;     // average haircut
const GUITAR_SOLO_SEC   = 30;     // average guitar solo length
const RAMEN_MIN         = 3;      // instant ramen cooking time

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

  const totalHours = totalMinutes / 60;
  const shiftCount = completedShifts.length;
  const avgWeekHrs = totalHours / 4.33;
  const hasRate    = !!settings.hourly_rate;
  const totalGross = hasRate
    ? completedShifts.reduce(
        (sum, s) => sum + (calculateShiftPay(s, settings)?.total_gross ?? 0),
        0
      )
    : 0;

  const isAboveAvg = avgWeekHrs >= AVG_WEEK_HRS;
  const hasNight   = completedShifts.some((s) => {
    const h = new Date(s.start_time).getUTCHours();
    return h >= 20 || h < 6;
  });

  // ── Derived values (computed once, used across pool) ─────────
  const movies        = Math.max(1, Math.floor(totalHours / AVG_MOVIE_HRS));
  const books         = Math.max(1, Math.floor(totalHours / AVG_BOOK_HRS));
  const drivesEilat   = (totalHours / TEL_EILAT_HRS).toFixed(1);
  const coffees       = Math.round((totalHours * 60) / COFFEE_RITUAL_MIN);
  const flights       = (totalHours / TLV_JFK_HRS).toFixed(1);
  const bbRuns        = Math.max(1, Math.floor(totalHours / BREAKING_BAD_HRS));
  const workdays      = (totalHours / AVG_WORKDAY_HRS).toFixed(1);
  const gymSessions   = Math.floor(totalHours);
  const stepKilo      = ((totalHours * 2 * 4000) / 1000).toFixed(0);
  const issOrbits     = Math.round((totalHours * 60) / ISS_ORBIT_MIN);
  const snailKm       = (totalHours * SNAIL_KMH).toFixed(1);
  const earthPct      = ((totalHours / EARTH_WALK_HRS) * 100).toFixed(1);
  const beePct        = ((totalHours / BEE_LIFETIME_HRS) * 100).toFixed(1);
  const minWageEarned = Math.round(totalHours * MIN_WAGE_ILS);
  const degreePct     = ((totalHours / DEGREE_HRS) * 100).toFixed(1);
  const titanicPct    = ((totalHours / TITANIC_HRS) * 100 * 100).toFixed(4);
  const hatikvaCount  = Math.round((totalHours * 60) / HATIKVA_MIN).toLocaleString();
  const drumPct       = ((totalHours / DRUM_RECORD_HRS) * 100).toFixed(1);
  const sharkKm       = Math.round(totalHours * SHARK_KMH).toLocaleString();
  const wikiArticles  = Math.floor(totalHours / WIKIPEDIA_HRS);
  const surfSessions  = Math.floor(totalHours / SURF_HRS);
  const tacos         = Math.round((totalHours * 60) / TACO_MIN).toLocaleString();
  const shwarmas      = Math.round((totalHours * 60) / SHWARMA_MIN).toLocaleString();
  const sunPct        = ((totalHours / TLAV_SUNSHINE_HRS) * 100).toFixed(1);
  const armyPct       = ((totalHours / ARMY_SERVICE_HRS) * 100).toFixed(1);
  const shakespearePct = ((totalHours / SHAKESPEARE_HRS) * 100).toFixed(2);
  const carouselRides = Math.round((totalHours * 60) / CAROUSEL_MIN).toLocaleString();
  const monopolyGames = Math.floor(totalHours / MONOPOLY_HRS);
  const haircuts      = Math.floor((totalHours * 60) / HAIRCUT_MIN).toLocaleString();
  const guitarSolos   = Math.round((totalHours * 3600) / GUITAR_SOLO_SEC).toLocaleString();
  const ramenBowls    = Math.round((totalHours * 60) / RAMEN_MIN).toLocaleString();
  const pizzaCount    = hasRate ? Math.floor(totalGross / PIZZA_PRICE_ILS) : 0;
  const vsAvgMonth    = ((totalHours / AVG_MONTH_HRS) * 100).toFixed(0);

  // ── Pool ──────────────────────────────────────────────────────
  const pool: DailyInsight[] = [

    // ── Useful / good to know ──────────────────────────────────

    {
      icon: "🇮🇱",
      title: "vs. Israeli Average",
      text: `You averaged **${avgWeekHrs.toFixed(1)}h/week** this month. The Israeli CBS average is **${AVG_WEEK_HRS}h/week** — you're ${isAboveAvg ? "pushing above the curve. Respect." : "keeping a healthy, balanced pace."}`,
      color: "indigo",
    },
    {
      icon: "📅",
      title: "Workday Counter",
      text: `**${Math.round(totalHours)}h** = **${workdays} standard Israeli workdays** at ${AVG_WORKDAY_HRS}h each. ${totalHours > AVG_WORKDAY_HRS * 21.5 ? "You put in extra this month — seriously, take a break. ☕" : "Solid, consistent effort."}`,
      color: "indigo",
    },
    {
      icon: "💡",
      title: "Monthly Output",
      text: `The average Israeli works **${AVG_MONTH_HRS}h/month**. You hit **${Math.round(totalHours)}h** — that's **${vsAvgMonth}%** of the national monthly average.`,
      color: "sky",
    },
    {
      icon: "🎓",
      title: "Degree Progress",
      text: `A full university degree takes roughly **${DEGREE_HRS.toLocaleString()} hours** of study. Your work this month = **${degreePct}%** of an entire degree. Knowledge is power.`,
      color: "indigo",
    },
    {
      icon: "📊",
      title: "Minimum Wage Check",
      text: `At Israel's minimum wage of **₪${MIN_WAGE_ILS}/hr**, your **${Math.round(totalHours)}h** would have earned **₪${minWageEarned.toLocaleString()}** before tax. Know your worth.`,
      color: "emerald",
    },
    {
      icon: "🐝",
      title: "Bee Standard",
      text: `A worker bee forages for a total of **${BEE_LIFETIME_HRS}h** in its entire lifetime. This month you worked **${beePct}%** of a full bee lifespan. Buzz buzz.`,
      color: "amber",
    },
    {
      icon: "☀️",
      title: "Tel Aviv Sunshine",
      text: `Tel Aviv enjoys **${TLAV_SUNSHINE_HRS.toLocaleString()} sunshine hours** a year. Your work this month equals **${sunPct}%** of all that golden light. Indoors the whole time.`,
      color: "amber",
    },
    {
      icon: "🌐",
      title: "Wikipedia Contributor",
      text: `The average Wikipedia article takes **~${WIKIPEDIA_HRS}h** to write. Your hours this month = **${wikiArticles} Wikipedia articles** worth of effort. The internet thanks you.`,
      color: "sky",
    },
    {
      icon: "🛡️",
      title: "Army Service",
      text: `Israeli male army service is about **${ARMY_SERVICE_HRS.toLocaleString()}h** of active duty. Your work this month got you **${armyPct}%** of the way there. Eyes open.`,
      color: "indigo",
    },

    // ── Fun comparisons ────────────────────────────────────────

    {
      icon: "🎬",
      title: "Movie Marathon",
      text: `Your **${Math.round(totalHours)}h** this month equals **${movies} full movies** back to back. No bathroom breaks allowed.`,
      color: "violet",
    },
    {
      icon: "📚",
      title: "Book Club",
      text: `**${Math.round(totalHours)}h** of work = **${books} books** read cover to cover. Your to-read list doesn't stand a chance.`,
      color: "amber",
    },
    {
      icon: "🚗",
      title: "Road Trip",
      text: `Your hours this month equal **${drivesEilat} drives** from Tel Aviv to Eilat. Sand dunes, desert wind, and deadlines.`,
      color: "emerald",
    },
    {
      icon: "☕",
      title: "Coffee Math",
      text: `Your **${Math.round(totalHours)}h** of work required roughly **${coffees} cups of coffee** — at 20 minutes of ritual each. No decaf.`,
      color: "amber",
    },
    {
      icon: "✈️",
      title: "Airborne Hours",
      text: `**${Math.round(totalHours)}h** worked = **${flights} non-stop El Al flights** from Tel Aviv to New York. Jet lag not included.`,
      color: "sky",
    },
    {
      icon: "📺",
      title: "Binge Watch",
      text: `**${Math.round(totalHours)}h** of work = **${bbRuns}× through Breaking Bad** (all ${BREAKING_BAD_HRS}h). Better call it a productive month.`,
      color: "violet",
    },
    {
      icon: "💪",
      title: "Gym Sessions",
      text: `**${Math.round(totalHours)}h** of work = **${gymSessions} one-hour gym sessions** you could've had. The real six-pack? Your work ethic.`,
      color: "emerald",
    },
    {
      icon: "🏖️",
      title: "Beach Tax",
      text: `You clocked **${shiftCount} shift${shiftCount !== 1 ? "s" : ""}** this month. That's **${shiftCount} potential beach days** surrendered to productivity. Worth it?`,
      color: "sky",
    },
    {
      icon: "🏄",
      title: "Surf's Up",
      text: `An average surf session runs **${SURF_HRS}h**. Your work this month = **${surfSessions} surf sessions** you could have caught instead. Hang ten.`,
      color: "sky",
    },
    {
      icon: "🎮",
      title: "Gamer vs. Worker",
      text: `The average person games **~${AVG_GAMER_HRS}h/month**. You worked **${Math.round(totalHours)}h** — ${totalHours > AVG_GAMER_HRS ? `**${(Math.round((totalHours / AVG_GAMER_HRS) * 10) / 10)}× more** time grinding IRL.` : "still leaving room for side quests."}`,
      color: "violet",
    },
    {
      icon: "🎲",
      title: "Board Game Champion",
      text: `An average Monopoly game takes **${MONOPOLY_HRS}h**. Your work this month = **${monopolyGames} full Monopoly games**. You've been collecting rent in real life.`,
      color: "violet",
    },
    {
      icon: "🚶",
      title: "Step Counter",
      text: `If each work hour were a 30-min walk, you'd have logged **${stepKilo}k steps** this month. Your fitness tracker would be very proud.`,
      color: "sky",
    },
    {
      icon: "🎭",
      title: "Shakespeare Mode",
      text: `Experts estimate Shakespeare spent **${SHAKESPEARE_HRS.toLocaleString()}h** writing all his works. This month you completed **${shakespearePct}%** of a Shakespeare. To work, or not to work.`,
      color: "violet",
    },

    // ── Completely ridiculous ──────────────────────────────────

    {
      icon: "🚀",
      title: "Orbital Mechanic",
      text: `The ISS orbits Earth every **${ISS_ORBIT_MIN} minutes**. While you worked **${Math.round(totalHours)}h** this month, it completed **${issOrbits} full laps** around the planet. Perspective.`,
      color: "indigo",
    },
    {
      icon: "🐌",
      title: "Snail Race",
      text: `A garden snail tops out at **${SNAIL_KMH} km/h**. In your **${Math.round(totalHours)}h** of work this month, a snail could have travelled **${snailKm} km**. Speedy little thing.`,
      color: "emerald",
    },
    {
      icon: "🌍",
      title: "Walk the Planet",
      text: `Walking around Earth's circumference at 5 km/h takes **${EARTH_WALK_HRS.toLocaleString()}h**. Your work this month is **${earthPct}%** of the way around the globe. Keep walking.`,
      color: "emerald",
    },
    {
      icon: "🦈",
      title: "Shark Sprint",
      text: `A great white shark hits **${SHARK_KMH} km/h**. In your **${Math.round(totalHours)} working hours** this month, a shark could swim **${sharkKm} km**. Don't go in the water.`,
      color: "rose",
    },
    {
      icon: "🥁",
      title: "Drum Solo",
      text: `The world's longest drum solo lasted **${DRUM_RECORD_HRS}h**. Your work this month is **${drumPct}%** of that record. You're basically a professional drummer.`,
      color: "rose",
    },
    {
      icon: "🎵",
      title: "National Anthem",
      text: `HaTikva runs for **${HATIKVA_MIN} minutes**. You could have listened to it **${hatikvaCount} times** during your working hours this month. 🎶 כֹּל עוֹד בַּלֵּבָב...`,
      color: "indigo",
    },
    {
      icon: "🌮",
      title: "Taco Economy",
      text: `A taco takes **${TACO_MIN} minutes** to make. In your **${Math.round(totalHours)}h** worked this month, you could have assembled **${tacos} tacos**. That's a lot of guac.`,
      color: "rose",
    },
    {
      icon: "🥙",
      title: "Shwarma Count",
      text: `At **${SHWARMA_MIN} minutes** per shwarma, your **${Math.round(totalHours)}h** this month = **${shwarmas} shwarmas** devoured. Israel's true unit of time.`,
      color: "amber",
    },
    {
      icon: "💇",
      title: "Haircut Economy",
      text: `A haircut takes about **${HAIRCUT_MIN} minutes**. You worked enough time this month for **${haircuts} haircuts**. You'd be completely bald by now.`,
      color: "violet",
    },
    {
      icon: "🎸",
      title: "Guitar Hero",
      text: `The average guitar solo lasts **${GUITAR_SOLO_SEC} seconds**. Your **${Math.round(totalHours)}h** worked = **${guitarSolos} guitar solos** — shredding nonstop all month.`,
      color: "rose",
    },
    {
      icon: "🍜",
      title: "Ramen Reserve",
      text: `Instant ramen is ready in **${RAMEN_MIN} minutes**. Your work hours this month could have powered **${ramenBowls} bowls of ramen**. That's a lot of sodium.`,
      color: "amber",
    },
    {
      icon: "🎠",
      title: "Carousel Rides",
      text: `A carousel ride lasts **${CAROUSEL_MIN} minutes**. You worked enough this month for **${carouselRides} carousel rides**. Round and round and round you go.`,
      color: "violet",
    },
    {
      icon: "🏗️",
      title: "Titanic Scale",
      text: `It took **3 million man-hours** to build the Titanic. Your work this month represents **${titanicPct}%** of that total. Small but unsinkable.`,
      color: "sky",
    },

    // ── Conditional (rate / night shifts) ─────────────────────

    ...(hasRate && totalGross > 0
      ? [
          {
            icon: "🍕",
            title: "Pizza Power",
            text: `Your gross earnings this month could buy **${pizzaCount} pizzas** at **₪${PIZZA_PRICE_ILS}** each. Dinner is sorted — for several months.`,
            color: "rose" as InsightColor,
          },
        ]
      : []),
    ...(hasNight
      ? [
          {
            icon: "🌙",
            title: "Night Owl",
            text: `You clocked **late-night shifts** this month. While Tel Aviv sleeps, you grind. **Israel respects the hustle.**`,
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
