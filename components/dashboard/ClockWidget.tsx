"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

type Phase = "dawn" | "day" | "dusk" | "night";

function getPhase(h: number): Phase {
  if (h >= 5 && h < 9)  return "dawn";
  if (h >= 9 && h < 16) return "day";
  if (h >= 16 && h < 20) return "dusk";
  return "night";
}

const PHASE_STOPS: Record<Phase, [string, string, string]> = {
  dawn:  ["#4133C8", "#9A6BFF", "#FF9E7D"],
  day:   ["#5B4DF2", "#7C5CF6", "#16C8D6"],
  dusk:  ["#6D4AFF", "#B14DFF", "#FF6FB0"],
  night: ["#3A2F8F", "#7C7CFF", "#22D8D0"],
};

function phaseGrad(phase: Phase, deg = 118) {
  const s = PHASE_STOPS[phase];
  return `linear-gradient(${deg}deg, ${s[0]} 0%, ${s[1]} 46%, ${s[2]} 108%)`;
}

// Animated aurora mesh background
const AuroraMesh = React.memo(function AuroraMesh({ phase }: { phase: Phase }) {
  const c = PHASE_STOPS[phase];
  function hexA(hex: string, a: number) {
    const h = hex.replace("#", "");
    const n = parseInt(h, 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  const bg = [
    `radial-gradient(38% 32% at 18% 14%, ${hexA(c[0], 0.22)}, transparent 70%)`,
    `radial-gradient(40% 36% at 88% 8%, ${hexA(c[1], 0.20)}, transparent 72%)`,
    `radial-gradient(46% 40% at 78% 92%, ${hexA(c[2], 0.18)}, transparent 72%)`,
    `radial-gradient(44% 38% at 8% 88%, ${hexA(c[1], 0.18)}, transparent 72%)`,
  ].join(", ");
  return (
    <div
      className="au-mesh"
      aria-hidden
      style={{
        position: "absolute",
        inset: "-12%",
        zIndex: 0,
        background: bg,
        filter: "blur(8px)",
      }}
    />
  );
});

// Subtle film grain overlay
const Grain = React.memo(function Grain() {
  return (
    <div
      aria-hidden
      style={{
        position: "absolute",
        inset: 0,
        zIndex: 5,
        pointerEvents: "none",
        opacity: 0.045,
        mixBlendMode: "multiply",
        backgroundImage:
          "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E\")",
        backgroundSize: "160px 160px",
      }}
    />
  );
});

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
  const [phase, setPhase] = useState<Phase>(() => getPhase(new Date().getHours()));
  const [bloom, setBloom] = useState(false);
  const prevClockedRef = useRef(activeShift !== null);

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

  // Update phase every minute
  useEffect(() => {
    const id = setInterval(() => setPhase(getPhase(new Date().getHours())), 60000);
    return () => clearInterval(id);
  }, []);

  // Bloom on clock-in
  useEffect(() => {
    const isClockedIn = activeShift !== null;
    if (!prevClockedRef.current && isClockedIn) {
      setBloom(true);
      const t = setTimeout(() => setBloom(false), 900);
      prevClockedRef.current = isClockedIn;
      return () => clearTimeout(t);
    }
    prevClockedRef.current = isClockedIn;
  }, [activeShift]);

  const isClockedIn = activeShift !== null;
  const dailyThresholdSeconds = dailyThresholdMinutes * 60;
  const progress = isClockedIn ? Math.min(1, elapsedSeconds / dailyThresholdSeconds) : 0;
  const isOvertime = elapsedSeconds > dailyThresholdSeconds;

  const handlePress = useCallback(async () => {
    if (isClockedIn) await onClockOut();
    else await onClockIn();
  }, [isClockedIn, onClockOut, onClockIn]);

  const sharedProps = useMemo(
    () => ({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress: handlePress, phase, bloom }),
    [isClockedIn, elapsedSeconds, isOvertime, loading, handlePress, phase, bloom]
  );

  if (clockStyle === "bold") return <BoldClock {...sharedProps} />;
  if (clockStyle === "night") return <NightClock {...sharedProps} />;
  if (clockStyle === "retro") return <RetroClock {...sharedProps} />;
  if (clockStyle === "minimal") return <MinimalClock {...sharedProps} />;
  if (clockStyle === "focus") return <FocusClock {...sharedProps} />;
  if (clockStyle === "aurora") return <AuroraClock {...sharedProps} progress={progress} bloom={bloom} />;
  if (clockStyle === "pulse") return <PulseClock {...sharedProps} />;
  if (clockStyle === "dial") return <DialClock {...sharedProps} progress={progress} />;
  if (clockStyle === "strand") return <StrandClock {...sharedProps} bloom={bloom} />;
  if (clockStyle === "prism") return <PrismClock {...sharedProps} progress={progress} bloom={bloom} />;

  // ── Classic — Aurora comet progress ring ──────────────────
  const size = 200, strokeWidth = 11, radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - progress);
  const ang = 2 * Math.PI * progress - Math.PI / 2;
  const cometX = size / 2 + radius * Math.cos(ang);
  const cometY = size / 2 + radius * Math.sin(ang);
  const gradId = `clf-${phase}`;
  const stops = PHASE_STOPS[phase];

  return (
    <div
      className="rounded-3xl overflow-hidden relative animate-scale-in"
      style={{ background: "white", border: "1px solid rgba(255,255,255,0.9)" }}
    >
      {/* Aurora mesh */}
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>

      <div className="relative z-10 p-5 flex flex-col items-center gap-4">
        {/* Status pill */}
        <div
          className="flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-bold tracking-widest uppercase transition-all duration-400"
          style={
            isClockedIn
              ? { background: phaseGrad(phase), color: "white", boxShadow: "0 8px 20px -8px rgba(91,77,242,0.6)" }
              : { background: "var(--au-surface-sub)", color: "var(--au-faint)" }
          }
        >
          <span
            className="h-1.5 w-1.5 rounded-full"
            style={
              isClockedIn
                ? { background: "#fff", boxShadow: "0 0 0 3px rgba(255,255,255,0.32)", animation: "auPulse 2s ease-in-out infinite" }
                : { background: "var(--au-faint)" }
            }
          />
          {isClockedIn ? "On Shift · Regular" : "Not Clocked In"}
        </div>

        {/* Progress ring */}
        <div className="relative" style={{ width: size, height: size }}>
          {isClockedIn && (
            <div
              aria-hidden
              style={{
                position: "absolute",
                inset: -6,
                borderRadius: "50%",
                background: phaseGrad(phase),
                filter: "blur(26px)",
                opacity: 0.22,
              }}
            />
          )}
          {bloom && (
            <div
              aria-hidden
              style={{
                position: "absolute",
                inset: 0,
                borderRadius: "50%",
                border: `10px solid ${PHASE_STOPS[phase][1]}`,
                animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards",
                pointerEvents: "none",
              }}
            />
          )}
          <svg
            width={size}
            height={size}
            style={{ position: "relative", transform: "rotate(-90deg)" }}
          >
            <defs>
              <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor={stops[0]} />
                <stop offset="52%" stopColor={stops[1]} />
                <stop offset="100%" stopColor={stops[2]} />
              </linearGradient>
            </defs>
            <circle
              cx={size / 2} cy={size / 2} r={radius}
              fill="none" stroke="#ECEBFA" strokeWidth={strokeWidth}
            />
            <circle
              cx={size / 2} cy={size / 2} r={radius}
              fill="none"
              stroke={`url(#${gradId})`}
              strokeWidth={strokeWidth}
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={offset}
              style={{ transition: "stroke-dashoffset 1s cubic-bezier(0.3, 0.8, 0.3, 1)" }}
            />
          </svg>
          {/* Comet dot */}
          {progress > 0.001 && (
            <div
              style={{
                position: "absolute",
                left: cometX - 9,
                top: cometY - 9,
                width: 18,
                height: 18,
                borderRadius: 999,
                background: "#fff",
                boxShadow: `0 0 0 4px var(--au-pop), 0 4px 16px var(--au-pop)`,
                transition: "all 1s cubic-bezier(0.3, 0.8, 0.3, 1)",
              }}
            />
          )}
          {/* Center content */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {isClockedIn ? (
              <>
                <span
                  className="text-xs font-bold uppercase tracking-widest mb-1"
                  style={{ color: "var(--au-faint)" }}
                >
                  ELAPSED
                </span>
                <span
                  key={Math.floor(elapsedSeconds / 60)}
                  className="font-bold tabular-nums leading-none"
                  style={{
                    fontFamily: "var(--au-display)",
                    fontSize: elapsedSeconds >= 3600 ? 32 : 40,
                    letterSpacing: "-0.03em",
                    color: "var(--au-ink)",
                    animation: "auCountSettle 0.8s 0.2s both",
                  }}
                  suppressHydrationWarning
                >
                  {formatHMS(elapsedSeconds)}
                </span>
                <span className="text-xs font-medium mt-1" style={{ color: "var(--au-ink-2)" }}>
                  since <b style={{ color: "var(--au-ink)" }}>{formatStartTime(activeShift!.start_time)}</b>
                </span>
              </>
            ) : (
              <>
                <span
                  className="text-4xl font-bold tabular-nums leading-none"
                  style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.03em" }}
                  suppressHydrationWarning
                >
                  {new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
                <span className="text-xs font-bold uppercase tracking-widest mt-1" style={{ color: "var(--au-faint)" }}>
                  ready
                </span>
              </>
            )}
          </div>
        </div>

        {/* Running info mini cards */}
        {isClockedIn && (
          <div className="flex gap-2 w-full">
            <div className="flex-1 rounded-[15px] px-3 py-2.5" style={{ background: "var(--au-surface-sub)" }}>
              <div className="text-[10px] font-bold uppercase tracking-widest mb-0.5" style={{ color: "var(--au-faint)" }}>Daily Goal</div>
              <div className="text-base font-bold" style={{ color: "var(--au-ink)" }}>
                {(dailyThresholdMinutes / 60).toFixed(0)}h
              </div>
            </div>
            <div className="flex-1 rounded-[15px] px-3 py-2.5" style={{ background: "var(--au-surface-sub)" }}>
              <div className="text-[10px] font-bold uppercase tracking-widest mb-0.5" style={{ color: "var(--au-faint)" }}>Progress</div>
              <div
                className="text-base font-bold"
                style={{ color: isOvertime ? "var(--au-peach-deep)" : "var(--au-indigo)" }}
              >
                {Math.round(progress * 100)}%{isOvertime && " OT"}
              </div>
            </div>
            {onEditStartTime && (
              <button
                type="button"
                onClick={onEditStartTime}
                className="flex-1 rounded-[15px] px-3 py-2.5 text-left transition-all hover:opacity-80"
                style={{ background: "var(--au-surface-sub)" }}
                aria-label="Edit start time"
              >
                <div className="text-[10px] font-bold uppercase tracking-widest mb-0.5" style={{ color: "var(--au-faint)" }}>Started</div>
                <div className="text-base font-bold flex items-center gap-1" style={{ color: "var(--au-ink)" }}>
                  {formatStartTime(activeShift!.start_time)}
                  <svg className="h-3 w-3 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24" style={{ color: "var(--au-faint)" }}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536M9 13l6.586-6.586a2 2 0 112.828 2.828L11.828 15.828a2 2 0 01-1.414.586H8v-2.414a2 2 0 01.586-1.414z" />
                  </svg>
                </div>
              </button>
            )}
          </div>
        )}

        {!isClockedIn && (
          <p className="text-sm" style={{ color: "var(--au-ink-2)" }}>
            Tap to start tracking your shift
          </p>
        )}

        {/* CTA Button */}
        <button
          onClick={handlePress}
          disabled={loading}
          className="clock-btn relative w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all duration-200 active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed"
          style={
            isClockedIn
              ? {
                  background: "transparent",
                  color: "var(--au-indigo)",
                  border: `1.5px solid var(--au-indigo)`,
                }
              : {
                  background: "var(--au-grad)",
                  color: "#fff",
                  border: "none",
                  boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)",
                }
          }
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
    </div>
  );
}

// ── Shared clock props ────────────────────────────────────────────────────────

interface SharedProps {
  isClockedIn: boolean;
  elapsedSeconds: number;
  isOvertime: boolean;
  loading: boolean;
  onPress: () => Promise<void>;
  phase: Phase;
  bloom?: boolean;
}

// ── Minimal style ─────────────────────────────────────────────────────────────

const MinimalClock = React.memo(function MinimalClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase }: SharedProps) {
  const [h, m] = (isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })).split(":");
  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-5 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="flex items-center gap-2">
          <span
            className="h-2 w-2 rounded-full"
            style={isClockedIn ? { background: "#10b981", animation: "auPulse 2s ease-in-out infinite" } : { background: "var(--au-faint)" }}
          />
          <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--au-faint)" }}>
            {isClockedIn ? "Shift Active" : "Not Clocked In"}
          </span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <div
            className="flex items-center tabular-nums leading-none"
            style={{
              fontFamily: "var(--au-display)",
              fontSize: 88,
              fontWeight: 400,
              letterSpacing: "-0.04em",
              lineHeight: 1,
              color: "var(--au-ink)",
            }}
            suppressHydrationWarning
          >
            <span key={h} style={{ animation: "auCountSettle 0.45s both" }}>{h}</span>
            <span
              className="rounded-full mx-2"
              style={{
                width: 12,
                height: 12,
                background: phaseGrad(phase),
                animation: "auPulse 1.6s ease-in-out infinite",
                boxShadow: isOvertime ? `0 0 14px var(--au-peach-deep)` : `0 0 14px var(--au-pop)`,
                display: "inline-block",
              }}
            />
            <span key={m} style={{ animation: "auCountSettle 0.45s both" }}>{m}</span>
          </div>
          {isClockedIn && (
            <span className="text-xs font-bold uppercase tracking-widest" style={{ color: isOvertime ? "var(--au-peach-deep)" : "var(--au-faint)" }}>
              {isOvertime ? "overtime" : "elapsed"}
            </span>
          )}
          <div
            className="rounded-full"
            style={{ width: 38, height: 4, background: phaseGrad(phase), opacity: isClockedIn ? 1 : 0.35 }}
          />
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-3.5 text-sm font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Bold style ────────────────────────────────────────────────────────────────

const BoldClock = React.memo(function BoldClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase }: SharedProps) {
  const timeStr = isClockedIn
    ? formatHMS(elapsedSeconds)
    : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const progress = isClockedIn ? Math.min(1, elapsedSeconds / (8 * 3600)) : 0;

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-6 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div
          key={isClockedIn ? Math.floor(elapsedSeconds / 60) : 0}
          className="tabular-nums leading-none text-center"
          style={{
            fontFamily: "var(--au-display)",
            fontWeight: 700,
            fontSize: 96,
            lineHeight: 0.88,
            letterSpacing: "-0.05em",
            background: phaseGrad(phase),
            WebkitBackgroundClip: "text",
            backgroundClip: "text",
            color: "transparent",
            animation: isClockedIn ? "auCountSettle 0.6s both" : "none",
          }}
          suppressHydrationWarning
        >
          {timeStr}
        </div>
        <div className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--au-faint)" }}>
          {isClockedIn ? (isOvertime ? "Overtime" : "On Shift · Regular") : "Ready to Track"}
        </div>
        <div className="w-full h-2 rounded-full overflow-hidden" style={{ background: "#ECEBFA" }}>
          <div
            className="h-full rounded-full relative overflow-hidden"
            style={{
              width: `${progress * 100}%`,
              background: phaseGrad(phase),
              transition: "width 1s cubic-bezier(0.3, 0.8, 0.3, 1)",
            }}
          >
            {isClockedIn && (
              <div
                aria-hidden
                style={{
                  position: "absolute",
                  inset: 0,
                  background: "linear-gradient(90deg,transparent 0%,rgba(255,255,255,0.38) 50%,transparent 100%)",
                  backgroundSize: "200% 100%",
                  animation: "auShimmer 1.8s linear infinite",
                }}
              />
            )}
          </div>
        </div>
        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Focus style ───────────────────────────────────────────────────────────────

const FocusClock = React.memo(function FocusClock({ isClockedIn, elapsedSeconds, loading, onPress, phase }: SharedProps) {
  const stops = PHASE_STOPS[phase];
  const timeStr = isClockedIn
    ? formatHMS(elapsedSeconds)
    : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-6 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="relative" style={{ width: 220, height: 220 }}>
          {/* Concentric breathing rings — staggered scale + opacity */}
          {[1, 0.74, 0.5].map((s, i) => (
            <div
              key={i}
              style={{
                position: "absolute",
                top: "50%",
                left: "50%",
                width: 220 * s,
                height: 220 * s,
                transform: "translate(-50%, -50%)",
                borderRadius: "50%",
                border: `${1.5 + i * 0.5}px solid ${stops[1]}`,
                opacity: 0.14 + i * 0.1,
                animation: isClockedIn
                  ? `auBreathe ${5.4 - i * 0.5}s cubic-bezier(0.45,0,0.55,1) infinite`
                  : "none",
                animationDelay: `${i * 0.55}s`,
                boxShadow: i === 2 && isClockedIn ? `0 0 24px rgba(${parseInt(stops[1].slice(1,3),16)},${parseInt(stops[1].slice(3,5),16)},${parseInt(stops[1].slice(5,7),16)},0.25)` : "none",
              }}
            />
          ))}
          <div
            style={{
              position: "absolute",
              top: "50%",
              left: "50%",
              transform: "translate(-50%, -50%)",
              width: 120,
              height: 120,
              borderRadius: "50%",
              background: `radial-gradient(circle, rgba(${parseInt(stops[0].slice(1, 3), 16)},${parseInt(stops[0].slice(3, 5), 16)},${parseInt(stops[0].slice(5, 7), 16)},0.2), transparent 70%)`,
              animation: isClockedIn ? "auBreathe 5s cubic-bezier(0.45,0,0.55,1) infinite" : "none",
            }}
          />
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <div className="text-xs font-bold uppercase tracking-widest mb-1.5" style={{ color: "var(--au-faint)" }}>
              {isClockedIn ? "IN FOCUS" : "READY"}
            </div>
            <div
              className="tabular-nums leading-none"
              style={{
                fontFamily: "var(--au-display)",
                fontSize: 40,
                fontWeight: 500,
                color: "var(--au-ink)",
                letterSpacing: "-0.02em",
              }}
              suppressHydrationWarning
            >
              {timeStr}
            </div>
          </div>
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Night style ───────────────────────────────────────────────────────────────

const NightClock = React.memo(function NightClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, bloom }: SharedProps) {
  const size = 220, stroke = 11, r = (size - stroke) / 2, c = 2 * Math.PI * r;
  const prog = isClockedIn ? Math.min(1, elapsedSeconds / (8 * 3600)) : 0;
  const target = c * (1 - prog);
  const timeStr = isClockedIn
    ? formatHMS(elapsedSeconds)
    : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const stars: [number, number][] = [[20, 30], [70, 16], [88, 44], [30, 78], [82, 80], [12, 60], [56, 90], [44, 12]];
  // Comet position at arc tip
  const cometAngle = -Math.PI / 2 + prog * 2 * Math.PI;
  const cometX = size / 2 + r * Math.cos(cometAngle);
  const cometY = size / 2 + r * Math.sin(cometAngle);

  return (
    <div
      className="rounded-3xl overflow-hidden relative animate-scale-in p-5 flex flex-col items-center gap-4"
      style={{ background: "radial-gradient(120% 100% at 50% 0%, #241F4E 0%, #14112E 55%, #0C0A22 100%)" }}
    >
      {stars.map((p, i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: `${p[0]}%`,
            top: `${p[1]}%`,
            width: 2.5,
            height: 2.5,
            borderRadius: 999,
            background: "#fff",
            animation: `auTwinkle ${2 + (i % 3)}s ease-in-out infinite`,
            animationDelay: `${i * 0.3}s`,
          }}
        />
      ))}

      <div className="relative" style={{ width: size, height: size }}>
        {bloom && (
          <div
            aria-hidden
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: "50%",
              border: "10px solid #7C7CFF",
              animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards",
              pointerEvents: "none",
            }}
          />
        )}
        <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
          <defs>
            <linearGradient id="ngt-g" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#7C7CFF" />
              <stop offset="50%" stopColor="#9A6BFF" />
              <stop offset="100%" stopColor="#3CE8E0" />
            </linearGradient>
          </defs>
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth={stroke} />
          <circle
            cx={size / 2} cy={size / 2} r={r}
            fill="none"
            stroke="url(#ngt-g)"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={c}
            strokeDashoffset={target}
            style={{ filter: "drop-shadow(0 0 8px rgba(124,124,255,0.9))", transition: "stroke-dashoffset 1s cubic-bezier(0.3, 0.8, 0.3, 1)" }}
          />
        </svg>
        {/* Comet dot at arc tip */}
        {prog > 0.008 && (
          <div
            style={{
              position: "absolute",
              left: cometX - 9,
              top: cometY - 9,
              width: 18,
              height: 18,
              borderRadius: 999,
              background: "#fff",
              boxShadow: "0 0 0 4px rgba(124,124,255,0.5), 0 0 18px rgba(124,124,255,0.9)",
              transition: "all 1s cubic-bezier(0.3,0.8,0.3,1)",
              pointerEvents: "none",
            }}
          />
        )}
        <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
          <div className="text-xs font-bold uppercase tracking-widest mb-1.5" style={{ color: "rgba(255,255,255,0.5)" }}>
            {isClockedIn ? "Night Shift" : "🌙 Late"}
          </div>
          <div
            className="tabular-nums leading-none"
            style={{
              fontFamily: "var(--au-display)",
              fontSize: 38,
              fontWeight: 500,
              letterSpacing: "-0.02em",
              color: "#fff",
              textShadow: "0 0 24px rgba(124,124,255,0.6)",
            }}
            suppressHydrationWarning
          >
            {timeStr}
          </div>
          {isClockedIn && (
            <div className="text-xs mt-1.5" style={{ color: "rgba(255,255,255,0.5)" }}>
              {isOvertime ? "+25% night rate" : "shift running"}
            </div>
          )}
        </div>
      </div>

      <button
        onClick={onPress}
        disabled={loading}
        className="clock-btn relative w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
        style={
          isClockedIn
            ? { background: "rgba(124,124,255,0.1)", color: "#9A9DFF", border: "1.5px solid rgba(124,124,255,0.35)" }
            : { background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.5)", border: "1.5px solid rgba(255,255,255,0.12)" }
        }
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
});

// ── Retro style ───────────────────────────────────────────────────────────────

function FlipCard({ digit, phase }: { digit: string; phase: Phase }) {
  return (
    <div
      style={{
        position: "relative",
        width: 72,
        height: 90,
        borderRadius: 14,
        background: "linear-gradient(180deg,#2B2746,#1C1933)",
        boxShadow: "0 12px 26px -12px rgba(20,15,50,0.7), inset 0 1px 0 rgba(255,255,255,0.08)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
      }}
    >
      <span
        key={digit}
        className="tabular-nums"
        style={{
          fontFamily: "var(--au-display)",
          fontSize: 54,
          fontWeight: 600,
          color: "#F4F3FF",
          letterSpacing: "-0.02em",
          animation: "auFlip 0.28s cubic-bezier(0.22,0.8,0.28,1)",
        }}
      >
        {digit}
      </span>
      <div style={{ position: "absolute", left: 0, right: 0, top: "50%", height: 2, background: "rgba(0,0,0,0.45)", transform: "translateY(-1px)" }} />
      <div style={{ position: "absolute", left: -3, top: "50%", width: 6, height: 12, borderRadius: 3, background: "#15122B", transform: "translateY(-6px)" }} />
      <div style={{ position: "absolute", right: -3, top: "50%", width: 6, height: 12, borderRadius: 3, background: "#15122B", transform: "translateY(-6px)" }} />
    </div>
  );
}

const RetroClock = React.memo(function RetroClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase }: SharedProps) {
  const stops = PHASE_STOPS[phase];
  let d1 = "0", d2 = "0", d3 = "0", d4 = "0", sub = "";
  if (isClockedIn) {
    const h = Math.floor(elapsedSeconds / 3600);
    const m = Math.floor((elapsedSeconds % 3600) / 60);
    const s = elapsedSeconds % 60;
    const hh = String(h).padStart(2, "0");
    const mm = String(m).padStart(2, "0");
    [d1, d2] = hh.split("");
    [d3, d4] = mm.split("");
    sub = `${String(s).padStart(2, "0")} SEC`;
  } else {
    const t = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    const parts = t.replace(" ", "").split(":");
    const hh = parts[0].padStart(2, "0");
    const mm = (parts[1] || "00").replace(/[^\d]/g, "").padStart(2, "0");
    [d1, d2] = hh.split("");
    [d3, d4] = mm.split("");
    sub = t.includes("AM") ? "AM" : t.includes("PM") ? "PM" : "";
  }

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in p-5 flex flex-col items-center gap-4" style={{ background: "#0D0B1E" }}>
      <AuroraMesh phase={phase} />
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="text-xs font-bold uppercase tracking-widest" style={{ color: "rgba(255,255,255,0.35)", letterSpacing: "0.18em" }}>
          {isClockedIn ? "ELAPSED" : "NOW"}
        </div>
        <div className="flex items-center gap-1.5">
          <FlipCard digit={d1} phase={phase} />
          <FlipCard digit={d2} phase={phase} />
          <div className="flex flex-col gap-2 mx-0.5">
            <span className="w-2 h-2 rounded-full" style={{ background: phaseGrad(phase) }} />
            <span className="w-2 h-2 rounded-full" style={{ background: phaseGrad(phase) }} />
          </div>
          <FlipCard digit={d3} phase={phase} />
          <FlipCard digit={d4} phase={phase} />
        </div>
        <div className="text-sm font-bold tracking-widest uppercase" style={{ color: isClockedIn ? stops[1] : "rgba(255,255,255,0.3)" }}>
          {sub}
        </div>
        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "rgba(91,77,242,0.15)", color: stops[1], border: `1.5px solid rgba(91,77,242,0.35)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Aurora style (gradient ring) ─────────────────────────────────────────────

const AuroraClock = React.memo(function AuroraClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase, progress, bloom }: SharedProps & { progress: number }) {
  const size = 200, strokeWidth = 14, radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.min(1, progress);
  const offset = circumference * (1 - clamped);
  const cometAngle = -Math.PI / 2 + clamped * 2 * Math.PI;
  const cometX = size / 2 + radius * Math.cos(cometAngle);
  const cometY = size / 2 + radius * Math.sin(cometAngle);
  const stops = PHASE_STOPS[phase];

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-5 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="flex items-center gap-2">
          <span
            className="h-2 w-2 rounded-full"
            style={isClockedIn ? { background: "#10b981", animation: "auPulse 2s ease-in-out infinite" } : { background: "var(--au-faint)" }}
          />
          <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--au-faint)" }}>
            {isClockedIn ? "Shift Active" : "Not Clocked In"}
          </span>
        </div>

        <div className="relative" style={{ width: size, height: size }}>
          {bloom && (
            <div
              aria-hidden
              style={{
                position: "absolute",
                inset: 0,
                borderRadius: "50%",
                border: `10px solid ${PHASE_STOPS[phase][1]}`,
                animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards",
                pointerEvents: "none",
              }}
            />
          )}
          <svg width={size} height={size} className="absolute inset-0">
            <defs>
              <linearGradient id={`au-ring-${phase}`} x1="0" y1="0" x2={size} y2={size} gradientUnits="userSpaceOnUse">
                {PHASE_STOPS[phase].map((c, i) => (
                  <stop key={i} offset={`${i * 50}%`} stopColor={c} />
                ))}
              </linearGradient>
            </defs>
            <circle cx={size / 2} cy={size / 2} r={radius} strokeWidth={strokeWidth} stroke="#ECEBFA" fill="none" strokeLinecap="round" />
            <circle
              cx={size / 2} cy={size / 2} r={radius}
              strokeWidth={strokeWidth}
              stroke={`url(#au-ring-${phase})`}
              fill="none"
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={offset}
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
              style={{ transition: "stroke-dashoffset 1s cubic-bezier(0.16, 1, 0.3, 1)" }}
            />
          </svg>
          {/* Comet dot at arc tip */}
          {clamped > 0.005 && (
            <div
              style={{
                position: "absolute",
                left: cometX - 9,
                top: cometY - 9,
                width: 18,
                height: 18,
                borderRadius: 999,
                background: "#fff",
                boxShadow: `0 0 0 4px ${stops[1]}, 0 0 16px ${stops[1]}`,
                transition: "all 1s cubic-bezier(0.3, 0.8, 0.3, 1)",
                pointerEvents: "none",
              }}
            />
          )}
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1">
            <span
              className="font-bold tabular-nums leading-none"
              style={{
                fontFamily: "var(--au-display)",
                fontSize: elapsedSeconds >= 3600 ? 28 : 36,
                background: phaseGrad(phase),
                WebkitBackgroundClip: "text",
                backgroundClip: "text",
                color: "transparent",
              }}
              suppressHydrationWarning
            >
              {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
            {isClockedIn && (
              <span className="text-xs font-semibold" style={{ background: phaseGrad(phase), WebkitBackgroundClip: "text", backgroundClip: "text", color: "transparent" }}>
                {isOvertime ? "overtime" : "elapsed"}
              </span>
            )}
          </div>
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-3.5 text-sm font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Pulse style ───────────────────────────────────────────────────────────────

const PulseClock = React.memo(function PulseClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase, bloom }: SharedProps) {
  const stops = PHASE_STOPS[phase];
  const ringColor = isClockedIn ? (isOvertime ? "#FF9E7D" : stops[1]) : "#E2E0F2";
  const glowColor = isClockedIn ? (isOvertime ? "rgba(255,158,125,0.5)" : "rgba(91,77,242,0.5)") : "transparent";

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-6 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="relative" style={{ width: 164, height: 164 }}>
          {/* Sonar ping rings when active */}
          {isClockedIn && [0, 1].map((i) => (
            <div
              key={`sonar-${i}`}
              className="absolute rounded-full"
              style={{
                top: "50%",
                left: "50%",
                width: 60,
                height: 60,
                transform: "translate(-50%, -50%)",
                border: `2px solid ${ringColor}`,
                animation: `auSonar 2.8s ease-out infinite`,
                animationDelay: `${i * 1.4}s`,
                willChange: "transform, opacity",
                pointerEvents: "none",
              }}
            />
          ))}
          {/* Bloom ring on clock-in */}
          {bloom && (
            <div
              aria-hidden
              style={{
                position: "absolute",
                top: "50%",
                left: "50%",
                width: 80,
                height: 80,
                transform: "translate(-50%, -50%)",
                borderRadius: "50%",
                border: `8px solid ${ringColor}`,
                animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards",
                pointerEvents: "none",
              }}
            />
          )}
          {/* Static concentric rings */}
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="absolute rounded-full border transition-all duration-700"
              style={{
                top: i * 20,
                right: i * 20,
                bottom: i * 20,
                left: i * 20,
                borderColor: ringColor,
                opacity: 1 - i * 0.28,
                animation: isClockedIn
                  ? `auBreathe ${2.8 - i * 0.3}s ${i === 0 ? "cubic-bezier(0.4,0,0.2,1)" : "ease-in-out"} infinite`
                  : "none",
                animationDelay: `${i * 0.2}s`,
                boxShadow: i < 2 && isClockedIn ? `0 0 ${28 - i * 10}px ${glowColor}, 0 0 ${56 - i * 20}px ${glowColor}` : "none",
              }}
            />
          ))}
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5">
            <span
              key={Math.floor(elapsedSeconds / 60)}
              className="font-bold tabular-nums leading-none"
              style={{
                fontFamily: "var(--au-display)",
                color: isClockedIn ? ringColor : "var(--au-faint)",
                textShadow: isClockedIn ? `0 0 18px ${glowColor}` : "none",
                fontSize: elapsedSeconds >= 3600 ? 14 : 18,
                animation: isClockedIn ? "auCountSettle 0.5s both" : "none",
              }}
              suppressHydrationWarning
            >
              {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
        </div>
        <p className="text-xs font-bold uppercase tracking-widest" style={{ color: isClockedIn ? ringColor : "var(--au-faint)" }}>
          {isClockedIn ? (isOvertime ? "overtime" : "shift active") : "standby"}
        </p>
        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: ringColor, border: `1.5px solid ${ringColor}` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Dial style ────────────────────────────────────────────────────────────────

const DialClock = React.memo(function DialClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase, progress }: SharedProps & { progress: number }) {
  const size = 200, cx = 100, cy = 100, outerR = 82, innerRMain = 72, innerRMinor = 77, sectorR = 60, labelR = 88;
  const clamped = Math.min(1, progress);
  const startRad = -Math.PI / 2;
  const endRad = startRad + clamped * 2 * Math.PI;
  const x1 = cx + Math.cos(startRad) * sectorR, y1 = cy + Math.sin(startRad) * sectorR;
  const x2 = cx + Math.cos(endRad) * sectorR, y2 = cy + Math.sin(endRad) * sectorR;
  const largeArc = clamped > 0.5 ? 1 : 0;
  const stops = PHASE_STOPS[phase];
  const sectorColor = isOvertime ? "rgba(255,158,125,0.25)" : `rgba(${parseInt(stops[0].slice(1, 3), 16)},${parseInt(stops[0].slice(3, 5), 16)},${parseInt(stops[0].slice(5, 7), 16)},0.12)`;

  const ticks = useMemo(() => Array.from({ length: 60 }, (_, i) => {
    const isMain = i % 5 === 0;
    const a = ((i * 6) - 90) * (Math.PI / 180);
    return {
      x1: cx + Math.cos(a) * (isMain ? innerRMain : innerRMinor),
      y1: cy + Math.sin(a) * (isMain ? innerRMain : innerRMinor),
      x2: cx + Math.cos(a) * outerR,
      y2: cy + Math.sin(a) * outerR,
      isMain,
    };
  }), []);

  const numbers = useMemo(() => Array.from({ length: 12 }, (_, i) => {
    const a = ((i * 30) - 90) * (Math.PI / 180);
    return { x: cx + Math.cos(a) * labelR, y: cy + Math.sin(a) * labelR, label: String(i * 5) };
  }), []);

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-5 flex flex-col items-center gap-3">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-3 w-full">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
          <circle cx={cx} cy={cy} r={outerR + 6} fill="white" fillOpacity={0.85} />
          {isClockedIn && clamped > 0 && (
            clamped >= 1
              ? <circle cx={cx} cy={cy} r={sectorR} fill={sectorColor} />
              : <path d={`M ${cx} ${cy} L ${x1.toFixed(2)} ${y1.toFixed(2)} A ${sectorR} ${sectorR} 0 ${largeArc} 1 ${x2.toFixed(2)} ${y2.toFixed(2)} Z`} fill={sectorColor} />
          )}
          {ticks.map((t, i) => (
            <line key={i} x1={t.x1.toFixed(2)} y1={t.y1.toFixed(2)} x2={t.x2.toFixed(2)} y2={t.y2.toFixed(2)}
              stroke={t.isMain ? "var(--au-ink-2)" : "var(--au-hair)"} strokeWidth={t.isMain ? 2 : 1} strokeLinecap="round" />
          ))}
          {numbers.map((n, i) => (
            <text key={i} x={n.x.toFixed(2)} y={n.y.toFixed(2)} textAnchor="middle" dominantBaseline="central"
              fontSize={9} fontWeight="600" fill="var(--au-faint)">{n.label}</text>
          ))}
          {isClockedIn && (
            <g style={{
              transform: `rotate(${-90 + clamped * 360}deg)`,
              transformOrigin: `${cx}px ${cy}px`,
              transition: "transform 1s cubic-bezier(0.3,0.8,0.3,1)",
            }}>
              <line x1={cx} y1={cy} x2={cx} y2={cy - 75}
                stroke={isOvertime ? "var(--au-peach)" : "var(--au-indigo)"} strokeWidth={3} strokeLinecap="round" />
            </g>
          )}
          <circle cx={cx} cy={cy} r={14} fill={isClockedIn ? "var(--au-indigo)" : "#e5e7eb"}
            style={isClockedIn ? { animation: "auPulse 2.5s ease-in-out infinite", transformBox: "fill-box", transformOrigin: "center" } : undefined} />
          {isClockedIn ? (
            <>
              <rect x={cx - 5} y={cy - 5} width={3.5} height={10} rx={1} fill="white" />
              <rect x={cx + 1.5} y={cy - 5} width={3.5} height={10} rx={1} fill="white" />
            </>
          ) : (
            <circle cx={cx} cy={cy} r={5} fill="var(--au-faint)" />
          )}
        </svg>

        <div className="text-center">
          <p
            className="text-3xl font-bold tabular-nums tracking-tight"
            style={{ fontFamily: "var(--au-display)", color: isClockedIn ? (isOvertime ? "var(--au-peach-deep)" : "var(--au-ink)") : "var(--au-faint)" }}
            suppressHydrationWarning
          >
            {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </p>
          {isClockedIn && (
            <p className="text-xs font-bold uppercase tracking-widest mt-0.5" style={{ color: isOvertime ? "var(--au-peach-deep)" : "var(--au-faint)" }}>
              {isOvertime ? "overtime" : "elapsed"}
            </p>
          )}
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Strand style ──────────────────────────────────────────────────────────────

const StrandClock = React.memo(function StrandClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase, bloom }: SharedProps) {
  const stops = PHASE_STOPS[phase];
  const progress = isClockedIn ? Math.min(1, elapsedSeconds / (8 * 3600)) : 0;
  const frontIndex = Math.floor(progress * 20);

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-5 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="relative w-full" style={{ height: 164 }}>
          {bloom && (
            <div
              aria-hidden
              style={{
                position: "absolute",
                inset: 0,
                borderRadius: 14,
                border: `8px solid ${stops[1]}`,
                animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards",
                pointerEvents: "none",
                zIndex: 3,
              }}
            />
          )}
          <svg
            viewBox="0 0 300 164"
            width="100%"
            height="164"
            style={{ position: "absolute", inset: 0, overflow: "visible" }}
          >
            <defs>
              <linearGradient id={`st-g-${phase}`} x1="0" y1="0" x2="0" y2="164" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor={stops[0]} />
                <stop offset="50%" stopColor={stops[1]} />
                <stop offset="100%" stopColor={stops[2]} />
              </linearGradient>
            </defs>
            {Array.from({ length: 20 }, (_, idx) => {
              const isLeft = idx < 10;
              const x = isLeft ? idx * 10 + 5 : (idx + 10) * 10 + 5;
              const isLit = idx < frontIndex;
              const isFront = idx === frontIndex && isClockedIn && progress > 0;
              if (isFront) {
                return (
                  <line key={idx} x1={x} y1={10} x2={x} y2={154}
                    stroke={stops[1]} strokeWidth={3} strokeLinecap="round"
                    style={{
                      filter: `drop-shadow(0 0 5px ${stops[1]})`,
                      animation: "auTwinkle 1.2s ease-in-out infinite",
                    }}
                  />
                );
              } else if (isLit) {
                return (
                  <line key={idx} x1={x} y1={10} x2={x} y2={154}
                    stroke={isOvertime && !isLeft ? "#EF6F45" : stops[1]}
                    strokeWidth={2} strokeLinecap="round"
                    style={{ opacity: 0.82 }}
                  />
                );
              } else {
                return (
                  <line key={idx} x1={x} y1={10} x2={x} y2={154}
                    stroke="#9B9AC4" strokeWidth={1.5} strokeLinecap="round"
                    style={{
                      animation: `auStrandFlicker ${3 + (idx % 4) * 0.5}s ease-in-out infinite`,
                      animationDelay: `${(idx * 0.18) % 2}s`,
                    }}
                  />
                );
              }
            })}
          </svg>
          {/* Time in center gap */}
          <div style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            transform: "translate(-50%, -50%)",
            zIndex: 2,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 2,
            pointerEvents: "none",
          }}>
            <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--au-faint)" }}>
              {isClockedIn ? "elapsed" : "ready"}
            </span>
            <span
              key={isClockedIn ? Math.floor(elapsedSeconds / 60) : 0}
              className="font-bold tabular-nums leading-none"
              style={{
                fontFamily: "var(--au-display)",
                fontSize: elapsedSeconds >= 3600 ? 28 : 36,
                letterSpacing: "-0.03em",
                color: "var(--au-ink)",
                animation: isClockedIn ? "auCountSettle 0.5s both" : "none",
              }}
              suppressHydrationWarning
            >
              {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});

// ── Prism style ───────────────────────────────────────────────────────────────

const PrismClock = React.memo(function PrismClock({ isClockedIn, elapsedSeconds, isOvertime, loading, onPress, phase, progress, bloom }: SharedProps & { progress: number }) {
  const stops = PHASE_STOPS[phase];
  const clamped = Math.min(1, progress);

  // Water-level fill: interpolate a horizontal slice of the triangle.
  // Triangle points in SVG coords: A(150,8) B(6,192) C(294,192), viewBox 300×200.
  // At fp=0 the polygon collapses to the base line; at fp=1 it fills the whole triangle.
  const fp = isClockedIn ? clamped : 0;
  const yWater = 192 - 184 * fp;
  const xLeft  = 6   + 144 * fp;
  const xRight = 294 - 144 * fp;
  const yPct  = (yWater / 2).toFixed(2);
  const xLPct = (xLeft  / 3).toFixed(2);
  const xRPct = (xRight / 3).toFixed(2);

  return (
    <div className="rounded-3xl overflow-hidden relative animate-scale-in bg-white border border-white/80 au-card p-5 flex flex-col items-center gap-4">
      <div className="absolute inset-0 overflow-hidden rounded-3xl" style={{ zIndex: 0 }}>
        <AuroraMesh phase={phase} />
        <Grain />
      </div>
      <div className="relative z-10 flex flex-col items-center gap-4 w-full">
        <div className="relative w-full" style={{ height: 200 }}>
          {/* Rising liquid fill — 4-point clip-path interpolates a horizontal water level */}
          <div style={{
            position: "absolute",
            inset: 0,
            clipPath: `polygon(${xLPct}% ${yPct}%, ${xRPct}% ${yPct}%, 98% 96%, 2% 96%)`,
            background: `linear-gradient(180deg, ${stops[0]} 0%, ${stops[1]} 55%, ${stops[2]} 100%)`,
            transition: "clip-path 1s ease",
            opacity: 0.55,
            zIndex: 1,
          }} />

          {/* SVG: outline, orbiting segment, vertex rays, bloom */}
          <svg viewBox="0 0 300 200" width="100%" height="200"
            style={{ position: "absolute", inset: 0, zIndex: 2 }}>
            <defs>
              <linearGradient id={`pr-seg-${phase}`} x1="0" y1="0" x2="1" y2="1">
                <stop stopColor={stops[0]} /><stop offset="1" stopColor={stops[2]} />
              </linearGradient>
            </defs>
            {bloom && (
              <polygon points="150,8 6,192 294,192" fill="none" stroke={stops[1]} strokeWidth={7}
                style={{ animation: "auBloom 0.9s cubic-bezier(0.2,0.6,0.3,1) forwards" }} />
            )}
            {/* Triangle outline */}
            <polygon points="150,8 6,192 294,192" fill="none"
              stroke={stops[0]} strokeWidth={1.5} strokeOpacity={0.4} />
            {/* Water surface shimmer line */}
            {fp > 0.005 && (
              <line
                x1={xLeft.toFixed(1)} y1={yWater.toFixed(1)}
                x2={xRight.toFixed(1)} y2={yWater.toFixed(1)}
                stroke="rgba(255,255,255,0.55)" strokeWidth={2}
                style={{ filter: `drop-shadow(0 0 4px ${stops[1]})`, transition: "all 1s ease" }}
              />
            )}
            {/* Orbiting luminous segment — perimeter ≈ 755px */}
            {isClockedIn && (
              <polygon points="150,8 6,192 294,192" fill="none"
                stroke={`url(#pr-seg-${phase})`} strokeWidth={3} strokeLinecap="round"
                strokeDasharray="70 685"
                style={{
                  animation: `auPrismOrbit ${isOvertime ? 3 : 6}s linear infinite`,
                  filter: `drop-shadow(0 0 5px ${stops[1]})`,
                }}
              />
            )}
            {/* Vertex rays */}
            {isClockedIn && ([
              [150, 8, stops[0], 0],
              [6, 192, stops[1], 0.85],
              [294, 192, stops[2], 1.7],
            ] as [number, number, string, number][]).map(([x, y, c, delay], i) => (
              <circle key={i} cx={x} cy={y} r={5} fill={c}
                style={{
                  animation: "auPulse 2.5s ease-in-out infinite",
                  animationDelay: `${delay}s`,
                  filter: `drop-shadow(0 0 4px ${c})`,
                  transformBox: "fill-box",
                  transformOrigin: "center",
                }}
              />
            ))}
          </svg>

          {/* Time at centroid (~64% down) */}
          <div style={{
            position: "absolute",
            left: "50%",
            top: "64%",
            transform: "translate(-50%, -50%)",
            zIndex: 3,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 2,
            pointerEvents: "none",
          }}>
            <span className="text-xs font-bold uppercase tracking-widest"
              style={{ color: clamped > 0.35 ? "rgba(255,255,255,0.85)" : "var(--au-faint)" }}>
              {isClockedIn ? "elapsed" : "ready"}
            </span>
            <span
              key={isClockedIn ? Math.floor(elapsedSeconds / 60) : 0}
              className="font-bold tabular-nums leading-none"
              style={{
                fontFamily: "var(--au-display)",
                fontSize: elapsedSeconds >= 3600 ? 28 : 36,
                letterSpacing: "-0.03em",
                color: clamped > 0.35 ? "#fff" : "var(--au-ink)",
                textShadow: clamped > 0.35 ? "0 1px 8px rgba(0,0,0,0.35)" : "none",
                animation: isClockedIn ? "auCountSettle 0.5s both" : "none",
                transition: "color 0.6s ease, text-shadow 0.6s ease",
              }}
              suppressHydrationWarning
            >
              {isClockedIn ? formatHMS(elapsedSeconds) : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
        </div>

        <button
          onClick={onPress}
          disabled={loading}
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60"
          style={
            isClockedIn
              ? { background: "transparent", color: "var(--au-indigo)", border: `1.5px solid var(--au-indigo)` }
              : { background: "var(--au-grad)", color: "#fff", border: "none", boxShadow: "0 14px 26px -10px rgba(91,77,242,0.7)" }
          }
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
              {isClockedIn ? "Clocking out…" : "Clocking in…"}
            </span>
          ) : isClockedIn ? "Clock Out" : "Clock In"}
        </button>
      </div>
    </div>
  );
});
