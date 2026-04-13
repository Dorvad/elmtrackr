"use client";

import type { DailyInsight, InsightColor } from "@/lib/shifts/insights";

interface InsightOfTheDayProps {
  insight: DailyInsight;
}

const colorMap: Record<
  InsightColor,
  { outer: string; badge: string; icon: string; title: string; text: string }
> = {
  indigo: {
    outer: "bg-gradient-to-br from-indigo-500 to-violet-600",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-indigo-100",
  },
  violet: {
    outer: "bg-gradient-to-br from-violet-500 to-purple-700",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-violet-100",
  },
  emerald: {
    outer: "bg-gradient-to-br from-emerald-500 to-teal-600",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-emerald-100",
  },
  amber: {
    outer: "bg-gradient-to-br from-amber-400 to-orange-500",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-amber-50",
  },
  rose: {
    outer: "bg-gradient-to-br from-rose-500 to-pink-600",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-rose-100",
  },
  sky: {
    outer: "bg-gradient-to-br from-sky-500 to-blue-600",
    badge: "bg-white/20 text-white",
    icon: "text-white",
    title: "text-white",
    text: "text-sky-100",
  },
};

export function InsightOfTheDay({ insight }: InsightOfTheDayProps) {
  const c = colorMap[insight.color];

  return (
    <div>
      <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-1 mb-2">
        Insight of the Day
      </h2>
      <div
        className={[
          "rounded-2xl shadow-lg p-5 animate-fade-in-up",
          c.outer,
        ].join(" ")}
      >
        {/* Badge row */}
        <div className="flex items-center justify-between mb-3">
          <span
            className={[
              "rounded-full text-[10px] font-bold uppercase tracking-widest px-2.5 py-1",
              c.badge,
            ].join(" ")}
          >
            Daily Insight
          </span>
          {/* Refreshes at midnight note */}
          <span className="text-[10px] text-white/50 font-medium">
            changes daily
          </span>
        </div>

        {/* Icon + title */}
        <div className="flex items-start gap-3 mb-2">
          <span className="text-4xl leading-none" role="img" aria-label={insight.title}>
            {insight.icon}
          </span>
          <div>
            <p className={`text-base font-extrabold leading-tight tracking-tight ${c.title}`}>
              {insight.title}
            </p>
            <p className={`text-sm leading-snug mt-1 ${c.text}`}>
              {insight.text}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
