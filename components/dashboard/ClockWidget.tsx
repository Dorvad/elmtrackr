"use client";

import { useEffect, useState } from "react";
import type { Shift } from "@/types";
import type { ClockStyle } from "@/types";
import { ProgressRing } from "@/components/ui/ProgressRing";

interface ClockWidgetProps {
  activeShift: Shift | null;
  onClockIn: () => Promise<void>;
  onClockOut: () => Promise<void>;
  onEditStartTime?: () => void;
  loading: boolean;
  dailyThresholdMinutes?: number;
  clockStyle?: ClockStyle;
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
  onEditStartTime,
  loading,
  dailyThresholdMinutes = 480,
  clockStyle = "classic",
}: ClockWidgetProps) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

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

  if (clockStyle === "bold") {
    return <BoldClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} />;
  }

  if (clockStyle === "night") {
    return <NightClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} />;
  }

  if (clockStyle === "retro") {
    return <RetroClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} />;
  }

  if (clockStyle === "minimal") {
    return <MinimalClock
      isClockedIn={isClockedIn}
      elapsedSeconds={elapsedSeconds}
      isOvertime={isOvertime}
      loading={loading}
      onPress={handlePress}
    />;
  }

  if (clockStyle === "focus") {
    return <FocusClock
      isClockedIn={isClockedIn}
      elapsedSeconds={elapsedSeconds}
      isOvertime={isOvertime}
      loading={loading}
      onPress={handlePress}
    />;
  }

  if (clockStyle === "aurora") {
    return <AuroraClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} progress={progress} />;
  }

  if (clockStyle === "pulse") {
    return <PulseClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} />;
  }

  if (clockStyle === "dial") {
    return <DialClock isClockedIn={isClockedIn} elapsedSeconds={elapsedSeconds} isOvertime={isOvertime} loading={loading} onPress={handlePress} progress={progress} />;
  }

  // ── Classic style (original) ───────────────────────────────────────────────
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
      {isClockedIn && (
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute -top-8 -right-8 w-40 h-40 rounded-full bg-violet-600/20 blur-3xl" />
          <div className="absolute -bottom-8 -left-8 w-40 h-40 rounded-full bg-indigo-500/20 blur-3xl" />
        </div>
      )}

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
                  key={Math.floor(elapsedSeconds / 60)}
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
                <span className="text-3xl font-bold text-gray-200" suppressHydrationWarning>
                  {new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
                <span className="text-xs text-gray-400 font-medium mt-0.5">ready</span>
              </>
            )}
          </div>
        </ProgressRing>
      </div>

      {isClockedIn && (
        <div className="flex items-center gap-4 text-center">
          <div className="flex flex-col items-center">
            <p className="text-xs text-indigo-400 font-medium uppercase tracking-wide">Started</p>
            {onEditStartTime ? (
              <button
                type="button"
                onClick={onEditStartTime}
                className="flex items-center gap-1 mt-0.5 group"
                aria-label="Edit start time"
              >
                <span className="text-sm font-bold text-white group-hover:text-indigo-200 transition-colors">
                  {formatStartTime(activeShift!.start_time)}
                </span>
                <svg
                  className="h-3 w-3 text-indigo-400 group-hover:text-indigo-200 transition-colors flex-shrink-0"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  viewBox="0 0 24 24"
                  aria-hidden
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M15.232 5.232l3.536 3.536M9 13l6.586-6.586a2 2 0 112.828 2.828L11.828 15.828a2 2 0 01-1.414.586H8v-2.414a2 2 0 01.586-1.414z"
                  />
                </svg>
              </button>
            ) : (
              <p className="text-sm font-bold text-white mt-0.5">
                {formatStartTime(activeShift!.start_time)}
              </p>
            )}
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

// ── Minimal style ─────────────────────────────────────────────────────────────

function MinimalClock({
  isClockedIn,
  elapsedSeconds,
  isOvertime,
  loading,
  onPress,
}: {
  isClockedIn: boolean;
  elapsedSeconds: number;
  isOvertime: boolean;
  loading: boolean;
  onPress: () => Promise<void>;
}) {
  return (
    <div className="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 flex flex-col items-center gap-4 animate-scale-in">
      <div className="flex items-center gap-2">
        <span
          className={[
            "h-2 w-2 rounded-full",
            isClockedIn ? "bg-emerald-400 animate-pulse" : "bg-gray-300",
          ].join(" ")}
        />
        <span className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          {isClockedIn ? "Shift Active" : "Not Clocked In"}
        </span>
      </div>

      <div className="flex flex-col items-center">
        <span
          className={[
            "text-5xl font-extrabold tabular-nums tracking-tight leading-none",
            isClockedIn
              ? isOvertime ? "text-amber-500" : "text-gray-900"
              : "text-gray-300",
          ].join(" ")}
          suppressHydrationWarning
        >
          {isClockedIn
            ? formatHMS(elapsedSeconds)
            : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
        </span>
        {isClockedIn && (
          <span className={`text-xs font-semibold mt-1.5 uppercase tracking-widest ${isOvertime ? "text-amber-400" : "text-gray-400"}`}>
            {isOvertime ? "overtime" : "elapsed"}
          </span>
        )}
      </div>

      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-3.5 text-sm font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60",
          isClockedIn
            ? "bg-gray-900 text-white hover:bg-gray-800"
            : "bg-indigo-600 text-white shadow-md shadow-indigo-500/25 hover:bg-indigo-700",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
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

// ── Focus style ───────────────────────────────────────────────────────────────

function FocusClock({
  isClockedIn,
  elapsedSeconds,
  isOvertime,
  loading,
  onPress,
}: {
  isClockedIn: boolean;
  elapsedSeconds: number;
  isOvertime: boolean;
  loading: boolean;
  onPress: () => Promise<void>;
}) {
  return (
    <div
      className={[
        "rounded-3xl p-6 flex flex-col items-center gap-6 animate-scale-in",
        isClockedIn
          ? "bg-gradient-to-br from-indigo-950 via-indigo-900 to-violet-900"
          : "bg-gray-950",
      ].join(" ")}
    >
      <span
        className={[
          "font-extrabold tabular-nums tracking-tighter leading-none",
          elapsedSeconds >= 3600 ? "text-5xl" : "text-6xl",
          isClockedIn
            ? isOvertime ? "text-amber-300" : "text-white"
            : "text-white/20",
        ].join(" ")}
        suppressHydrationWarning
      >
        {isClockedIn
          ? formatHMS(elapsedSeconds)
          : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
      </span>

      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60",
          isClockedIn
            ? "bg-white/15 text-white border border-white/20 hover:bg-white/20"
            : "bg-white/10 text-white/60 border border-white/10 hover:bg-white/15 hover:text-white",
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

// ── Bold style ────────────────────────────────────────────────────────────────

function BoldClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress,
}: { isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean; loading: boolean; onPress: () => Promise<void> }) {
  return (
    <div className="bg-white rounded-3xl border border-gray-100 shadow-sm p-6 flex flex-col items-center gap-5 animate-scale-in">
      <div className="flex items-center gap-2">
        <span className={["h-2 w-2 rounded-full", isClockedIn ? "bg-emerald-400 animate-pulse" : "bg-gray-200"].join(" ")} />
        <span className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          {isClockedIn ? "Shift Active" : "Not Clocked In"}
        </span>
      </div>
      <span
        className={[
          "font-black tabular-nums tracking-tighter leading-none",
          elapsedSeconds >= 3600 ? "text-5xl" : "text-6xl",
          isClockedIn ? (isOvertime ? "text-amber-500" : "text-gray-900") : "text-gray-200",
        ].join(" ")}
        suppressHydrationWarning
      >
        {isClockedIn
          ? formatHMS(elapsedSeconds)
          : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
      </span>
      {isClockedIn && (
        <span className={`text-xs font-bold uppercase tracking-widest ${isOvertime ? "text-amber-400" : "text-gray-400"}`}>
          {isOvertime ? "overtime" : "elapsed"}
        </span>
      )}
      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60",
          isClockedIn ? "bg-gray-900 text-white hover:bg-gray-800" : "bg-indigo-600 text-white shadow-md shadow-indigo-500/25 hover:bg-indigo-700",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}

// ── Night style ───────────────────────────────────────────────────────────────

function NightClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress,
}: { isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean; loading: boolean; onPress: () => Promise<void> }) {
  return (
    <div className="bg-gray-950 rounded-3xl border border-gray-800 p-5 flex flex-col items-center gap-5 animate-scale-in">
      <div className="flex items-center gap-2">
        <span className={["h-1.5 w-1.5 rounded-full", isClockedIn ? "bg-cyan-400 animate-pulse" : "bg-gray-700"].join(" ")} />
        <span className={`text-xs font-bold uppercase tracking-widest ${isClockedIn ? "text-cyan-700" : "text-gray-700"}`}>
          {isClockedIn ? "Shift Active" : "Standby"}
        </span>
      </div>
      <span
        className={[
          "font-extrabold tabular-nums tracking-tight leading-none",
          elapsedSeconds >= 3600 ? "text-4xl" : "text-5xl",
          isClockedIn ? (isOvertime ? "text-amber-400" : "text-cyan-400") : "text-gray-800",
        ].join(" ")}
        style={isClockedIn ? {
          textShadow: isOvertime ? "0 0 24px rgba(251,191,36,0.55)" : "0 0 24px rgba(34,211,238,0.5)",
        } : undefined}
        suppressHydrationWarning
      >
        {isClockedIn
          ? formatHMS(elapsedSeconds)
          : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
      </span>
      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60 border",
          isClockedIn
            ? "bg-cyan-950 text-cyan-300 border-cyan-900 hover:bg-cyan-900"
            : "bg-gray-900 text-gray-500 border-gray-800 hover:text-gray-300 hover:border-gray-700",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}

// ── Retro style ───────────────────────────────────────────────────────────────

function RetroClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress,
}: { isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean; loading: boolean; onPress: () => Promise<void> }) {
  return (
    <div className="bg-gray-950 rounded-3xl border border-gray-800 p-5 flex flex-col items-center gap-4 animate-scale-in">
      <div className="w-full bg-gray-900 rounded-2xl border border-gray-800 p-5 flex flex-col items-center gap-2">
        <span
          className={[
            "font-mono font-bold tabular-nums tracking-widest leading-none",
            elapsedSeconds >= 3600 ? "text-4xl" : "text-5xl",
            isClockedIn ? (isOvertime ? "text-red-400" : "text-amber-400") : "text-amber-950",
          ].join(" ")}
          style={isClockedIn ? {
            textShadow: isOvertime ? "0 0 12px rgba(248,113,113,0.6)" : "0 0 12px rgba(251,191,36,0.45)",
          } : undefined}
        >
          {isClockedIn ? formatHMS(elapsedSeconds) : "00:00"}
        </span>
        <span className={`text-[10px] font-mono tracking-[0.35em] uppercase ${isClockedIn ? (isOvertime ? "text-red-600" : "text-amber-700") : "text-amber-950"}`}>
          {isClockedIn ? (isOvertime ? "overtime" : "elapsed") : "standby"}
        </span>
      </div>
      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-4 text-base font-mono font-bold tracking-widest uppercase transition-all active:scale-95 disabled:opacity-60 border",
          isClockedIn
            ? "bg-amber-950 text-amber-400 border-amber-900 hover:bg-amber-900"
            : "bg-gray-900 text-amber-950 border-gray-800 hover:text-amber-800 hover:border-amber-950",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "out…" : "in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}

// ── Aurora style (gradient ring) ─────────────────────────────────────────────

function AuroraClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress, progress,
}: {
  isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean;
  loading: boolean; onPress: () => Promise<void>; progress: number;
}) {
  const size = 200;
  const strokeWidth = 14;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - Math.min(1, progress));

  return (
    <div className="bg-white rounded-3xl border border-gray-100 shadow-sm p-5 flex flex-col items-center gap-4 animate-scale-in">
      <div className="flex items-center gap-2">
        <span className={["h-2 w-2 rounded-full", isClockedIn ? "bg-emerald-400 animate-pulse" : "bg-gray-300"].join(" ")} />
        <span className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          {isClockedIn ? "Shift Active" : "Not Clocked In"}
        </span>
      </div>

      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="absolute inset-0">
          <defs>
            <linearGradient id="aurora-gradient" x1="0" y1="0" x2={size} y2={size} gradientUnits="userSpaceOnUse">
              <stop offset="0%"   stopColor={isOvertime ? "#f97316" : "#6366f1"} />
              <stop offset="35%"  stopColor={isOvertime ? "#ef4444" : "#a855f7"} />
              <stop offset="65%"  stopColor={isOvertime ? "#ef4444" : "#ec4899"} />
              <stop offset="100%" stopColor={isOvertime ? "#dc2626" : "#f97316"} />
            </linearGradient>
          </defs>
          <circle cx={size / 2} cy={size / 2} r={radius} strokeWidth={strokeWidth} stroke="#f1f5f9" fill="none" strokeLinecap="round" />
          <circle
            cx={size / 2} cy={size / 2} r={radius}
            strokeWidth={strokeWidth}
            stroke="url(#aurora-gradient)"
            fill="none"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
            style={{ transition: "stroke-dashoffset 1s cubic-bezier(0.16, 1, 0.3, 1)" }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-1">
          <span
            className="font-extrabold tabular-nums tracking-tight leading-none"
            style={{
              fontSize: elapsedSeconds >= 3600 ? "28px" : "36px",
              ...(isClockedIn ? {
                background: isOvertime
                  ? "linear-gradient(135deg, #f97316, #ef4444)"
                  : "linear-gradient(135deg, #6366f1, #a855f7, #ec4899)",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              } : { color: "#d1d5db" }),
            }}
            suppressHydrationWarning
          >
            {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </span>
          {isClockedIn && (
            <span
              className="text-xs font-semibold"
              style={{
                background: isOvertime
                  ? "linear-gradient(135deg, #f97316, #ef4444)"
                  : "linear-gradient(135deg, #6366f1, #ec4899)",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}
            >
              {isOvertime ? "overtime" : "elapsed"}
            </span>
          )}
        </div>
      </div>

      <button
        onClick={onPress}
        disabled={loading}
        className="w-full rounded-2xl py-3.5 text-sm font-bold text-white tracking-wide transition-all active:scale-95 disabled:opacity-60"
        style={{ background: isOvertime ? "linear-gradient(135deg, #f97316, #ef4444)" : "linear-gradient(135deg, #6366f1, #a855f7)" }}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-3.5 w-3.5 rounded-full border-2 border-white border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}

// ── Pulse style (glowing concentric rings) ────────────────────────────────────

function PulseClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress,
}: {
  isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean;
  loading: boolean; onPress: () => Promise<void>;
}) {
  const ringColor  = isClockedIn ? (isOvertime ? "#fbbf24" : "#22d3ee") : "#1f2937";
  const glowColor  = isClockedIn ? (isOvertime ? "rgba(251,191,36,0.45)" : "rgba(34,211,238,0.45)") : "transparent";
  const glowColor2 = isClockedIn ? (isOvertime ? "rgba(251,191,36,0.2)"  : "rgba(34,211,238,0.2)")  : "transparent";

  return (
    <div className="bg-gray-950 rounded-3xl border border-gray-800 p-6 flex flex-col items-center gap-4 animate-scale-in">
      <div className="relative" style={{ width: 164, height: 164 }}>
        {/* Outer ring */}
        <div
          className={["absolute inset-0 rounded-full border-2 transition-all duration-700", isClockedIn ? "animate-pulse" : ""].join(" ")}
          style={{ borderColor: ringColor, boxShadow: isClockedIn ? `0 0 20px ${glowColor}, 0 0 40px ${glowColor2}` : "none" }}
        />
        {/* Middle ring */}
        <div
          className="absolute rounded-full border transition-all duration-700"
          style={{ top: 16, right: 16, bottom: 16, left: 16, borderColor: ringColor, opacity: 0.65 }}
        />
        {/* Inner ring */}
        <div
          className="absolute rounded-full border transition-all duration-700"
          style={{ top: 32, right: 32, bottom: 32, left: 32, borderColor: ringColor, opacity: 0.35 }}
        />
        {/* Center content */}
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5">
          <svg width={18} height={18} viewBox="0 0 24 24" fill="none">
            <path
              d="M12 3v8M6.5 7.5A7 7 0 1 0 17.5 7.5"
              stroke={isClockedIn ? ringColor : "#374151"}
              strokeWidth={2.5}
              strokeLinecap="round"
            />
          </svg>
          <span
            className="font-bold tabular-nums leading-none tracking-tight"
            style={{
              color: isClockedIn ? ringColor : "#374151",
              textShadow: isClockedIn ? `0 0 14px ${glowColor}` : "none",
              fontSize: elapsedSeconds >= 3600 ? "13px" : "17px",
            }}
            suppressHydrationWarning
          >
            {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </span>
        </div>
      </div>

      <p
        className="text-xs font-bold uppercase tracking-widest"
        style={{ color: isClockedIn ? (isOvertime ? "#d97706" : "#155e75") : "#374151" }}
      >
        {isClockedIn ? (isOvertime ? "overtime" : "shift active") : "standby"}
      </p>

      <button
        onClick={onPress}
        disabled={loading}
        className="w-full rounded-2xl py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60 border"
        style={isClockedIn ? { background: "transparent", borderColor: ringColor, color: ringColor }
          : { background: "#0f172a", borderColor: "#1f2937", color: "#374151" }}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}

// ── Dial style (analog kitchen timer) ────────────────────────────────────────

function DialClock({
  isClockedIn, elapsedSeconds, isOvertime, loading, onPress, progress,
}: {
  isClockedIn: boolean; elapsedSeconds: number; isOvertime: boolean;
  loading: boolean; onPress: () => Promise<void>; progress: number;
}) {
  const size = 200;
  const cx = 100, cy = 100;
  const outerR    = 82;
  const innerRMain  = 72;
  const innerRMinor = 77;
  const sectorR   = 60;
  const labelR    = 88;
  const clamped   = Math.min(1, progress);

  const startRad  = -Math.PI / 2;
  const endRad    = startRad + clamped * 2 * Math.PI;
  const x1 = cx + Math.cos(startRad) * sectorR;
  const y1 = cy + Math.sin(startRad) * sectorR;
  const x2 = cx + Math.cos(endRad)   * sectorR;
  const y2 = cy + Math.sin(endRad)   * sectorR;
  const largeArc  = clamped > 0.5 ? 1 : 0;
  const sectorFill = isOvertime ? "rgba(239,68,68,0.18)" : "rgba(220,38,38,0.13)";

  const ticks = Array.from({ length: 60 }, (_, i) => {
    const isMain  = i % 5 === 0;
    const a = ((i * 6) - 90) * (Math.PI / 180);
    const r = isMain ? innerRMain : innerRMinor;
    return { x1: cx + Math.cos(a) * r, y1: cy + Math.sin(a) * r, x2: cx + Math.cos(a) * outerR, y2: cy + Math.sin(a) * outerR, isMain };
  });

  const numbers = Array.from({ length: 12 }, (_, i) => {
    const a = ((i * 30) - 90) * (Math.PI / 180);
    return { x: cx + Math.cos(a) * labelR, y: cy + Math.sin(a) * labelR, label: String(i * 5) };
  });

  return (
    <div className="bg-gray-50 rounded-3xl border border-gray-200 shadow-sm p-5 flex flex-col items-center gap-4 animate-scale-in">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        {/* Background disc */}
        <circle cx={cx} cy={cy} r={outerR + 6} fill="white" />

        {/* Swept sector */}
        {isClockedIn && clamped > 0 && (
          clamped >= 1 ? (
            <circle cx={cx} cy={cy} r={sectorR} fill={sectorFill} />
          ) : (
            <path
              d={`M ${cx} ${cy} L ${x1.toFixed(2)} ${y1.toFixed(2)} A ${sectorR} ${sectorR} 0 ${largeArc} 1 ${x2.toFixed(2)} ${y2.toFixed(2)} Z`}
              fill={sectorFill}
            />
          )
        )}

        {/* Tick marks */}
        {ticks.map((t, i) => (
          <line key={i}
            x1={t.x1.toFixed(2)} y1={t.y1.toFixed(2)}
            x2={t.x2.toFixed(2)} y2={t.y2.toFixed(2)}
            stroke={t.isMain ? "#374151" : "#d1d5db"}
            strokeWidth={t.isMain ? 2 : 1}
            strokeLinecap="round"
          />
        ))}

        {/* Minute numbers */}
        {numbers.map((n, i) => (
          <text key={i}
            x={n.x.toFixed(2)} y={n.y.toFixed(2)}
            textAnchor="middle" dominantBaseline="central"
            fontSize={9} fontWeight="600" fill="#6b7280"
          >
            {n.label}
          </text>
        ))}

        {/* Needle */}
        {isClockedIn && (
          <line
            x1={cx} y1={cy}
            x2={(cx + Math.cos(endRad) * 75).toFixed(2)}
            y2={(cy + Math.sin(endRad) * 75).toFixed(2)}
            stroke={isOvertime ? "#ef4444" : "#dc2626"}
            strokeWidth={3}
            strokeLinecap="round"
          />
        )}

        {/* Center cap */}
        <circle cx={cx} cy={cy} r={14} fill={isClockedIn ? "#1f2937" : "#e5e7eb"} />
        {isClockedIn ? (
          <>
            <rect x={cx - 5}   y={cy - 5} width={3.5} height={10} rx={1} fill="white" />
            <rect x={cx + 1.5} y={cy - 5} width={3.5} height={10} rx={1} fill="white" />
          </>
        ) : (
          <circle cx={cx} cy={cy} r={5} fill="#9ca3af" />
        )}
      </svg>

      <div className="text-center">
        <p
          className="text-3xl font-bold tabular-nums tracking-tight"
          style={{ color: isClockedIn ? (isOvertime ? "#ef4444" : "#111827") : "#d1d5db" }}
          suppressHydrationWarning
        >
          {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
        </p>
        {isClockedIn && (
          <p className="text-xs font-bold uppercase tracking-widest mt-0.5" style={{ color: isOvertime ? "#ef4444" : "#9ca3af" }}>
            {isOvertime ? "overtime" : "elapsed"}
          </p>
        )}
      </div>

      <button
        onClick={onPress}
        disabled={loading}
        className={[
          "w-full rounded-2xl py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60",
          isClockedIn ? "bg-gray-900 text-white hover:bg-gray-800" : "bg-indigo-600 text-white shadow-md shadow-indigo-500/25 hover:bg-indigo-700",
        ].join(" ")}
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
            {isClockedIn ? "Clocking out…" : "Clocking in…"}
          </span>
        ) : isClockedIn ? "Clock Out" : "Clock In"}
      </button>
    </div>
  );
}
