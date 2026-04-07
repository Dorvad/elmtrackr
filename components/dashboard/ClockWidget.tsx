"use client";

import { useEffect, useState } from "react";
import type { Shift, UserSettings } from "@/types";
import { elapsedMinutes, formatMinutes } from "@/lib/shifts/duration";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

interface ClockWidgetProps {
  activeShift: Shift | null;
  onClockIn: () => Promise<void>;
  onClockOut: () => Promise<void>;
  loading: boolean;
}

export function ClockWidget({
  activeShift,
  onClockIn,
  onClockOut,
  loading,
}: ClockWidgetProps) {
  const [elapsed, setElapsed] = useState<number>(0);

  // Tick every 30 seconds when clocked in
  useEffect(() => {
    if (!activeShift) return;
    const update = () =>
      setElapsed(elapsedMinutes(activeShift) ?? 0);
    update();
    const interval = setInterval(update, 30_000);
    return () => clearInterval(interval);
  }, [activeShift]);

  const isClockedIn = activeShift !== null;

  return (
    <Card
      className={[
        "transition-colors duration-300",
        isClockedIn ? "bg-blue-600 border-blue-500" : "bg-white",
      ].join(" ")}
    >
      <div className="flex flex-col items-center gap-4 py-2">
        {/* Status badge */}
        <div
          className={[
            "flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold",
            isClockedIn
              ? "bg-blue-500 text-white"
              : "bg-gray-100 text-gray-500",
          ].join(" ")}
        >
          <span
            className={[
              "h-1.5 w-1.5 rounded-full",
              isClockedIn ? "bg-green-300 animate-pulse" : "bg-gray-400",
            ].join(" ")}
          />
          {isClockedIn ? "Clocked In" : "Clocked Out"}
        </div>

        {/* Elapsed time */}
        {isClockedIn && (
          <div className="text-center">
            <p className="text-5xl font-bold text-white tabular-nums">
              {formatMinutes(elapsed)}
            </p>
            <p className="mt-1 text-sm text-blue-200">
              Started at{" "}
              {new Date(activeShift!.start_time).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit",
              })}
            </p>
          </div>
        )}

        {!isClockedIn && (
          <p className="text-gray-400 text-sm">Tap below to start your shift</p>
        )}

        {/* Action button */}
        <Button
          variant={isClockedIn ? "secondary" : "primary"}
          size="lg"
          loading={loading}
          fullWidth
          onClick={isClockedIn ? onClockOut : onClockIn}
          className={isClockedIn ? "!bg-white !text-blue-600 !border-0" : ""}
        >
          {isClockedIn ? "Clock Out" : "Clock In"}
        </Button>
      </div>
    </Card>
  );
}
