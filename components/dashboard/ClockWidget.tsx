"use client";

import { useEffect, useState } from "react";
import type { Shift } from "@/types";
import { ProgressRing } from "@/components/ui/ProgressRing";

interface ClockWidgetProps {
  activeShift: Shift | null;
  onClockIn: () => Promise<void>;
  onClockOut: () => Promise<void>;
  loading: boolean;
  dailyThresholdMinutes?: number;
}

function formatHMS(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

function formatStartTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function ClockWidget({
  activeShift,
  onClockIn,
  onClockOut,
  loading,
  dailyThresholdMinutes = 480,
}: ClockWidgetProps) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  // Tick every second when clocked in
  useEffect(() => {
    if (!activeShift) { setElapsedSeconds(0); return; }
    const update = () => {
      const diff = Math.max(
        0,
        Math.floor((Date.now() - new Date(activeShift.start_time).getTime()) / 1000)
      );
      setElapsedSeconds(diff);
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [activeShift]);

  const isClockedIn = activeShift !== null;
  const dailyThresholdSeconds = dailyThresholdMinutes * 60;
  const progress = isClockedIn
    ? Math.min(1, elapsedSeconds / dailyThresholdSeconds)
    : 0;

  const isOvertime = elapsedSeconds > dailyThresholdSeconds;

  async function handlePress() {
    if (isClockedIn) await onClockOut();
    else await onClockIn();
  }

  return (
    <div
      className={[
        "rounded-3xl p-5 flex flex-col items-center gap-5 overflow-hidden relative",
        "animate-scale-in",
        isClockedIn
          ? "bg-gradient-to-br from-indigo-950 via-indigo-900 to-violet-900"
          : "bg-white shadow-sm border border-gray-100",
      ].join(" ")}
    >
      {/* Ambient glow when active */}
      {isClockedIn && (
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute -top-8 -right-8 w-40 h-40 rounded-full bg-violet-600/20 blur-3xl" />
          <div className="absolute -bottom-8 -left-8 w-40 h-40 rounded-full bg-indigo-500/20 blur-3xl" />
        </div>
      )}

      {/* Status pill */}
      <div
        className={[
          "relative flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-bold tracking-wide uppercase",
          isClockedIn
            ? "bg-white/10 text-white/90 border border-white/20"
            : "bg-gray-100 text-gray-500",
        ].join(" ")}
      >
        <span
          className={[
            "h-1.5 w-1.5 rounded-full",
            isClockedIn ? "bg-emerald-400 animate-pulse" : "bg-gray-300",
          ].join(" ")}
        />
        {isClockedIn ? "Shift Active" : "Not Clocked In"}
      </div>

      {/* Progress ring + timer */}
      <div className="relative">
        <ProgressRing
          progress={progress}
          size={172}
          strokeWidth={9}
          color={isOvertime ? "stroke-amber-400" : "stroke-indigo-400"}
          trackColor={isClockedIn ? "stroke-white/10" : "stroke-indigo-100"}
        >
          <div className="flex flex-col items-center gap-0.5">
            {isClockedIn ? (
              <>
                <span
                  key={Math.floor(elapsedSeconds / 60)} // re-animate on minute change
                  className={[
                    "font-bold tabular-nums leading-none tracking-tight animate-ticker-in",
                    elapsedSeconds >= 3600 ? "text-3xl" : "text-4xl",
                    isOvertime ? "text-amber-300" : "text-white",
                  ].join(" ")}
                  style={{ animation: "ticker-in 0.3s ease both" }}
                >
                  {formatHMS(elapsedSeconds)}
                </span>
                <span className={[
                  "text-xs font-medium mt-0.5",
                  isOvertime ? "text-amber-400/80" : "text-indigo-300",
                ].join(" ")}>
                  {isOvertime ? "overtime" : "elapsed"}
                </span>
              </>
            ) : (
              <>
                <span className="text-3xl font-bold text-gray-200">
                  {new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
                <span className="text-xs text-gray-400 font-medium mt-0.5">ready</span>
              </>
            )}
          </div>
        </ProgressRing>
      </div>

      {/* Start time info */}
      {isClockedIn && (
        <div className="flex items-center gap-4 text-center">
          <div>
            <p className="text-xs text-indigo-400 font-medium uppercase tracking-wide">Started</p>
            <p className="text-sm font-bold text-white mt-0.5">
              {formatStartTime(activeShift!.start_time)}
            </p>
          </div>
          <div className="h-8 w-px bg-white/10" />
          <div>
            <p className="text-xs text-indigo-400 font-medium uppercase tracking-wide">Daily Goal</p>
            <p className="text-sm font-bold text-white mt-0.5">
              {(dailyThresholdMinutes / 60).toFixed(0)}h
            </p>
          </div>
          <div className="h-8 w-px bg-white/10" />
          <div>
            <p className="text-xs text-indigo-400 font-medium uppercase tracking-wide">Progress</p>
            <p className={[
              "text-sm font-bold mt-0.5",
              isOvertime ? "text-amber-300" : "text-white",
            ].join(" ")}>
              {Math.round(progress * 100)}%
            </p>
          </div>
        </div>
      )}

      {!isClockedIn && (
        <p className="text-gray-400 text-sm">
          Tap to start tracking your shift
        </p>
      )}

      {/* Action button */}
      <button
        onClick={handlePress}
        disabled={loading}
        className={[
          "clock-btn relative w-full rounded-2xl py-4 text-base font-bold tracking-wide",
          "transition-all duration-200 active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2",
          isClockedIn
            ? "bg-white/15 text-white border border-white/25 hover:bg-white/20 focus-visible:ring-white/50"
            : "bg-indigo-600 text-white shadow-lg shadow-indigo-500/30 hover:bg-indigo-700 focus-visible:ring-indigo-500",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? (
          "Clock Out"
        ) : (
          "Clock In"
        )}
      </button>
    </div>
  );
}
