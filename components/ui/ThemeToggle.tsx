"use client";

import { useTheme } from "@/hooks/useTheme";

// ── Small SVG icons ───────────────────────────────────────────────────────────

function SunIcon() {
  return (
    <svg
      className="h-4 w-4 flex-shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <circle cx="12" cy="12" r="5" />
      <path d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M2 12h2M20 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg
      className="h-4 w-4 flex-shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ThemeToggle() {
  const { isDark, toggle, mounted } = useTheme();

  // While unmounted, render a neutral skeleton so there's no layout shift
  if (!mounted) {
    return (
      <div className="relative w-full h-[52px] rounded-2xl bg-gray-100 overflow-hidden">
        <div className="absolute inset-0 animate-pulse bg-gray-200/60 rounded-2xl" />
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={`Switch to ${isDark ? "light" : "dark"} mode`}
      aria-pressed={isDark}
      className="relative w-full h-[52px] rounded-2xl overflow-hidden select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 active:scale-[0.98] transition-transform duration-100"
    >
      {/*
       * Two gradient backgrounds crossfade via opacity.
       * Light: soft sky-blue → lavender (daytime palette)
       * Dark:  deep indigo → violet (matches the ClockWidget active state exactly)
       */}
      <div
        className="absolute inset-0 transition-opacity duration-500"
        style={{
          background: "linear-gradient(to right, #e0f2fe, #ede9fe, #ddd6fe)",
          opacity: isDark ? 0 : 1,
        }}
      />
      <div
        className="absolute inset-0 transition-opacity duration-500"
        style={{
          background: "linear-gradient(to right, #1e1b4b, #312e81, #4c1d95)",
          opacity: isDark ? 1 : 0,
        }}
      />

      {/*
       * Star sparkles — fade in with dark mode.
       * Positioned in the right half of the track so they're only
       * visible behind the dark side.
       */}
      <div
        className="absolute inset-0 pointer-events-none transition-opacity duration-500"
        style={{ opacity: isDark ? 1 : 0 }}
        aria-hidden
      >
        <span className="absolute top-2.5 right-[4.5rem] w-1 h-1 rounded-full bg-white/70" />
        <span className="absolute bottom-3   right-[7rem]  w-0.5 h-0.5 rounded-full bg-white/50" />
        <span className="absolute top-4     right-[3rem]  w-0.5 h-0.5 rounded-full bg-white/60" />
        <span className="absolute top-2     right-[9rem]  w-1 h-1 rounded-full bg-white/30 animate-pulse" />
      </div>

      {/*
       * Sliding thumb — a pill that highlights the active half.
       * Spring easing (cubic-bezier with overshoot) gives a satisfying snap.
       */}
      <div
        className={[
          "absolute top-1.5 bottom-1.5 w-[48%] rounded-xl",
          isDark
            ? "bg-white/15 shadow shadow-white/5"
            : "bg-white shadow-md shadow-gray-200/80",
        ].join(" ")}
        style={{
          left: isDark ? "50.5%" : "1.5%",
          transition: "left 420ms cubic-bezier(0.34, 1.56, 0.64, 1)",
        }}
      />

      {/* Labels — text contrast flips with the active side */}
      <div className="absolute inset-0 flex items-center pointer-events-none">
        {/* Light label */}
        <span
          className="flex-1 flex items-center justify-center gap-1.5 text-sm font-bold transition-all duration-400"
          style={{
            color: isDark ? "rgba(255,255,255,0.30)" : "#1e1b4b",
            transitionDuration: "350ms",
          }}
        >
          <SunIcon />
          Light
        </span>

        {/* Dark label */}
        <span
          className="flex-1 flex items-center justify-center gap-1.5 text-sm font-bold transition-all duration-400"
          style={{
            color: isDark ? "rgba(255,255,255,0.95)" : "rgba(67,56,202,0.35)",
            transitionDuration: "350ms",
          }}
        >
          <MoonIcon />
          Dark
        </span>
      </div>
    </button>
  );
}
