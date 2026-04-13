"use client";

import { useRef, useState } from "react";
import type { ReactNode } from "react";
import type { DailyInsight, InsightColor } from "@/lib/shifts/insights";

/**
 * Parse **bold** markers into <strong> spans.
 * Anything wrapped in ** ** renders as bright white bold text.
 */
function parseBold(text: string): ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? (
      <strong key={i} className="text-white font-extrabold">
        {part.slice(2, -2)}
      </strong>
    ) : (
      part
    )
  );
}

interface InsightOfTheDayProps {
  insights: DailyInsight[];
}

const colorMap: Record<
  InsightColor,
  { gradient: string; badge: string; subtext: string }
> = {
  indigo:  { gradient: "from-indigo-500 to-violet-600",  badge: "bg-white/20 text-white", subtext: "text-indigo-100"  },
  violet:  { gradient: "from-violet-500 to-purple-700",  badge: "bg-white/20 text-white", subtext: "text-violet-100"  },
  emerald: { gradient: "from-emerald-500 to-teal-600",   badge: "bg-white/20 text-white", subtext: "text-emerald-100" },
  amber:   { gradient: "from-amber-400 to-orange-500",   badge: "bg-white/20 text-white", subtext: "text-amber-50"    },
  rose:    { gradient: "from-rose-500 to-pink-600",      badge: "bg-white/20 text-white", subtext: "text-rose-100"    },
  sky:     { gradient: "from-sky-500 to-blue-600",       badge: "bg-white/20 text-white", subtext: "text-sky-100"     },
};

export function InsightOfTheDay({ insights }: InsightOfTheDayProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [activeIdx, setActiveIdx] = useState(0);

  function handleScroll() {
    const el = scrollRef.current;
    if (!el) return;
    // Each card is 88% of the container width + a 12px gap
    const cardWidth = el.offsetWidth * 0.88 + 12;
    const idx = Math.round(el.scrollLeft / cardWidth);
    setActiveIdx(Math.max(0, Math.min(idx, insights.length - 1)));
  }

  return (
    <div>
      {/* Section label */}
      <div className="flex items-center justify-between px-1 mb-2">
        <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          Insights of the Day
        </h2>
        <span className="text-[10px] text-gray-400 font-medium">
          swipe · changes daily
        </span>
      </div>

      {/* Carousel — negative margin + padding to bleed to screen edge on narrow containers */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex gap-3 overflow-x-auto pb-1"
        style={{
          scrollSnapType: "x mandatory",
          WebkitOverflowScrolling: "touch",
          scrollbarWidth: "none",
          msOverflowStyle: "none",
        }}
      >
        {insights.map((insight, i) => {
          const c = colorMap[insight.color];
          return (
            <div
              key={i}
              className={[
                "flex-shrink-0 rounded-2xl shadow-md p-5 bg-gradient-to-br animate-fade-in-up",
                c.gradient,
              ].join(" ")}
              style={{
                scrollSnapAlign: "start",
                width: "88%",
                animationDelay: `${i * 0.08}s`,
              }}
            >
              {/* Top row */}
              <div className="flex items-center justify-between mb-4">
                <span
                  className={[
                    "rounded-full text-[10px] font-bold uppercase tracking-widest px-2.5 py-1",
                    c.badge,
                  ].join(" ")}
                >
                  {i + 1} of {insights.length}
                </span>
                <span className="text-white/40 text-[10px] font-medium">
                  {i === activeIdx ? "●" : "○"} today
                </span>
              </div>

              {/* Icon */}
              <p
                className="text-5xl leading-none mb-3"
                role="img"
                aria-label={insight.title}
              >
                {insight.icon}
              </p>

              {/* Title + text */}
              <p className="text-base font-extrabold text-white leading-tight tracking-tight mb-1">
                {insight.title}
              </p>
              <p className={`text-sm leading-snug ${c.subtext}`}>
                {parseBold(insight.text)}
              </p>
            </div>
          );
        })}

        {/* Right-side spacer so last card can fully snap */}
        <div className="flex-shrink-0 w-4" aria-hidden />
      </div>

      {/* Pagination dots */}
      <div className="flex justify-center gap-2 mt-3">
        {insights.map((_, i) => (
          <button
            key={i}
            aria-label={`Insight ${i + 1}`}
            onClick={() => {
              const el = scrollRef.current;
              if (!el) return;
              const cardWidth = el.offsetWidth * 0.88 + 12;
              el.scrollTo({ left: i * cardWidth, behavior: "smooth" });
            }}
            className={[
              "h-1.5 rounded-full transition-all duration-300",
              i === activeIdx
                ? "w-5 bg-indigo-500"
                : "w-1.5 bg-gray-200 hover:bg-gray-300",
            ].join(" ")}
          />
        ))}
      </div>
    </div>
  );
}
