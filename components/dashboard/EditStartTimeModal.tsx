"use client";

import { useState, useEffect } from "react";
import type { Shift, UserSettings } from "@/types";
import { calculateShiftPay, formatCurrency } from "@/lib/shifts/payroll";
import { Button } from "@/components/ui/Button";

interface EditStartTimeModalProps {
  activeShift: Shift;
  settings: UserSettings | null;
  onSave: (newStartIso: string) => Promise<void>;
  onClose: () => void;
}

// ── Datetime helpers ──────────────────────────────────────────────────────────

/**
 * Convert a UTC ISO string to a `datetime-local` input value in the user's
 * local browser timezone (the format required by <input type="datetime-local">).
 */
function toLocalDateTimeInput(isoString: string): string {
  const d = new Date(isoString);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

/** Format total seconds as "Xh Ym Zs" or "Ym Zs". */
function fmtElapsed(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  if (h > 0) return `${h}h ${pad(m)}m`;
  return `${pad(m)}m ${pad(s)}s`;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function EditStartTimeModal({
  activeShift,
  settings,
  onSave,
  onClose,
}: EditStartTimeModalProps) {
  const [inputValue, setInputValue] = useState(
    toLocalDateTimeInput(activeShift.start_time)
  );
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Re-render every second so the "currently" elapsed preview stays live.
  const [, tick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => tick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, []);

  // ── Validation ──────────────────────────────────────────────────────────
  const now = new Date();
  const currentStart = new Date(activeShift.start_time);

  // Parse what the user typed. datetime-local strings have no tz suffix, so
  // `new Date("2024-01-15T08:00")` is interpreted as local time — correct.
  const parsed = inputValue ? new Date(inputValue) : null;

  let validationError: string | null = null;
  if (parsed) {
    if (isNaN(parsed.getTime())) {
      validationError = "Invalid date or time.";
    } else if (parsed >= now) {
      validationError = "Start time must be in the past.";
    } else if (parsed > currentStart) {
      // Only allow moving the start time to an earlier moment.
      validationError = "You can only set an earlier start time here.";
    }
  }

  const isValid = !!parsed && !validationError;

  // ── Impact preview ──────────────────────────────────────────────────────
  const currentElapsedSec = Math.max(
    0,
    Math.floor((now.getTime() - currentStart.getTime()) / 1000)
  );
  const newElapsedSec = isValid
    ? Math.max(0, Math.floor((now.getTime() - parsed!.getTime()) / 1000))
    : null;

  // Pay estimate: simulate a completed shift ending right now.
  let payPreview: number | null = null;
  if (isValid && settings?.hourly_rate) {
    const simulated: Shift = {
      ...activeShift,
      start_time: parsed!.toISOString(),
      end_time: now.toISOString(),
    };
    payPreview = calculateShiftPay(simulated, settings)?.total_gross ?? null;
  }

  // ── Max for datetime-local (prevents picking a future time) ────────────
  const maxInput = toLocalDateTimeInput(now.toISOString());

  // ── Save ────────────────────────────────────────────────────────────────
  async function handleSave() {
    if (!isValid || !parsed) return;
    setSaving(true);
    setSaveError(null);
    try {
      await onSave(parsed.toISOString());
      onClose();
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "Failed to update start time.");
    } finally {
      setSaving(false);
    }
  }

  // ── Keyboard: Escape to close ───────────────────────────────────────────
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-start-title"
    >
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden
      />

      {/* Bottom sheet */}
      <div className="relative w-full max-w-md bg-white rounded-t-3xl safe-area-pb animate-slide-up">
        {/* Drag handle */}
        <div className="flex justify-center pt-3 pb-1">
          <div className="h-1 w-10 rounded-full bg-gray-200" />
        </div>

        <div className="px-5 pt-2 pb-8 flex flex-col gap-5">
          {/* Header */}
          <div>
            <h2
              id="edit-start-title"
              className="text-lg font-extrabold text-gray-900 tracking-tight"
            >
              Edit start time
            </h2>
            <p className="text-sm text-gray-400 mt-1 leading-snug">
              Update the actual time you started working. The timer will keep
              running from that time.
            </p>
          </div>

          {/* Input */}
          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="start-time-input"
              className="text-xs font-bold text-gray-600 uppercase tracking-wide"
            >
              Start time
            </label>
            <input
              id="start-time-input"
              type="datetime-local"
              value={inputValue}
              max={maxInput}
              onChange={(e) => {
                setInputValue(e.target.value);
                setSaveError(null);
              }}
              className={[
                "w-full rounded-xl border px-3 py-3 text-sm font-medium",
                "focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent",
                "transition-all",
                validationError
                  ? "border-red-300 bg-red-50 text-red-800"
                  : "border-gray-200 bg-white text-gray-800",
              ].join(" ")}
            />
            {validationError && (
              <p className="text-xs text-red-500 font-medium">{validationError}</p>
            )}
            {saveError && !validationError && (
              <p className="text-xs text-red-500 font-medium">{saveError}</p>
            )}
          </div>

          {/* Impact preview */}
          {newElapsedSec !== null && (
            <div className="rounded-2xl bg-indigo-50 border border-indigo-100 p-4">
              <p className="text-[10px] font-bold text-indigo-400 uppercase tracking-widest mb-3">
                Impact Preview
              </p>
              <div className="flex items-start gap-5">
                <div>
                  <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">
                    New elapsed
                  </p>
                  <p className="text-base font-extrabold text-indigo-700 mt-0.5">
                    {fmtElapsed(newElapsedSec)}
                  </p>
                </div>
                <div>
                  <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">
                    Currently
                  </p>
                  <p className="text-base font-extrabold text-gray-400 mt-0.5">
                    {fmtElapsed(currentElapsedSec)}
                  </p>
                </div>
                {payPreview !== null && (
                  <div>
                    <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">
                      Est. earnings
                    </p>
                    <p className="text-base font-extrabold text-emerald-600 mt-0.5">
                      {formatCurrency(payPreview)}
                    </p>
                  </div>
                )}
              </div>
              {payPreview !== null && (
                <p className="text-[10px] text-gray-400 mt-2">
                  Earnings estimate assumes you clock out now.
                </p>
              )}
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3">
            <Button
              type="button"
              variant="secondary"
              fullWidth
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </Button>
            <Button
              type="button"
              fullWidth
              loading={saving}
              disabled={!isValid || saving}
              onClick={handleSave}
            >
              Save
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
