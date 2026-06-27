"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  FELLOWSHIP_BACKGROUNDS,
  fellowshipBackgroundIndex,
  fellowshipBackgroundPath,
  fellowshipPartyPath,
} from "@/lib/clock-faces/fellowship";

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

interface FellowshipClockProps {
  isClockedIn: boolean;
  elapsedSeconds: number;
  isOvertime: boolean;
  loading: boolean;
  onPress: () => Promise<void>;
  onEditStartTime?: () => void;
  activeShiftStartTime?: string;
}

export function FellowshipClock({
  isClockedIn,
  elapsedSeconds,
  isOvertime,
  loading,
  onPress,
  onEditStartTime,
  activeShiftStartTime,
}: FellowshipClockProps) {
  const [hour, setHour] = useState(() => new Date().getHours());
  const targetIndex = fellowshipBackgroundIndex(hour);
  const [displayIndex, setDisplayIndex] = useState(targetIndex);
  const [fadeFromIndex, setFadeFromIndex] = useState<number | null>(null);

  useEffect(() => {
    const syncHour = () => {
      const next = new Date().getHours();
      setHour((prev) => (prev === next ? prev : next));
    };
    syncHour();
    const id = setInterval(syncHour, 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (targetIndex === displayIndex) return;
    setFadeFromIndex(displayIndex);
    setDisplayIndex(targetIndex);
    const t = setTimeout(() => setFadeFromIndex(null), 1400);
    return () => clearTimeout(t);
  }, [targetIndex, displayIndex]);

  const scene = FELLOWSHIP_BACKGROUNDS[displayIndex];
  const fadingScene = fadeFromIndex != null ? FELLOWSHIP_BACKGROUNDS[fadeFromIndex] : null;

  const handlePress = useCallback(async () => {
    await onPress();
  }, [onPress]);

  return (
    <div
      className="rounded-3xl overflow-hidden relative animate-scale-in border border-white/80"
      style={{ background: "#0c0a18" }}
    >
      {/* Scene — wide pixel-art background */}
      <div className="relative w-full aspect-[3/2] overflow-hidden">
        {fadingScene && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            key={`out-${fadingScene.id}`}
            src={fellowshipBackgroundPath(fadingScene.file)}
            alt=""
            aria-hidden
            className="absolute inset-0 w-full h-full object-cover object-bottom fellowship-bg-fade-out"
          />
        )}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          key={`in-${scene.id}`}
          src={fellowshipBackgroundPath(scene.file)}
          alt=""
          aria-hidden
          className={`absolute inset-0 w-full h-full object-cover object-bottom ${fadeFromIndex != null ? "fellowship-bg-fade-in" : ""}`}
        />

        {/* Location + time overlay */}
        <div
          className="absolute inset-x-0 top-0 z-20 px-3 pt-3 pb-8"
          style={{
            background: "linear-gradient(180deg, rgba(8,6,20,0.72) 0%, transparent 100%)",
          }}
        >
          <p
            className="text-[10px] font-bold uppercase tracking-[0.2em] text-center"
            style={{ color: "rgba(255,255,255,0.55)" }}
          >
            {scene.label}
          </p>
          <p
            className="text-center font-bold tabular-nums leading-none mt-1"
            style={{
              fontFamily: "var(--au-display)",
              fontSize: isClockedIn ? 28 : 32,
              color: isOvertime ? "var(--au-peach-deep)" : "#fff",
              textShadow: "0 2px 12px rgba(0,0,0,0.45)",
            }}
            suppressHydrationWarning
          >
            {isClockedIn
              ? formatHMS(elapsedSeconds)
              : new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </p>
          {isClockedIn && activeShiftStartTime && (
            <p className="text-center text-[11px] mt-1" style={{ color: "rgba(255,255,255,0.65)" }}>
              since {formatStartTime(activeShiftStartTime)}
            </p>
          )}
        </div>

        {/* Fellowship party — idle bob */}
        <div className="absolute inset-x-0 bottom-[10%] z-10 flex justify-center pointer-events-none px-2">
          <div className="fellowship-idle w-[88%] max-w-[340px]">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={fellowshipPartyPath()}
              alt=""
              aria-hidden
              className="w-full h-auto drop-shadow-[0_6px_18px_rgba(0,0,0,0.35)]"
              style={{ imageRendering: "pixelated" }}
            />
          </div>
        </div>
      </div>

      {/* Controls */}
      <div className="relative z-20 px-4 pb-4 pt-2 flex flex-col gap-2" style={{ background: "rgba(8,6,20,0.92)" }}>
        {isClockedIn && onEditStartTime && (
          <button
            type="button"
            onClick={onEditStartTime}
            aria-label="Edit start time"
            className="self-center text-xs font-semibold underline min-h-12 px-3"
            style={{ color: "rgba(255,255,255,0.55)" }}
          >
            Edit start time
          </button>
        )}
        <button
          type="button"
          onClick={handlePress}
          disabled={loading}
          aria-label={
            isClockedIn
              ? "Clock out. End your current shift."
              : "Clock in. Start tracking your shift."
          }
          className="clock-btn w-full rounded-[18px] py-4 text-base font-bold tracking-wide transition-all active:scale-95 disabled:opacity-60 min-h-12"
          style={
            isClockedIn
              ? {
                  background: "transparent",
                  color: "#fff",
                  border: "1.5px solid rgba(255,255,255,0.35)",
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
