"use client";

import { useState, FormEvent } from "react";
import type { ShiftFormData, CompensationProfile } from "@/types";
import { validateShiftTimes } from "@/lib/shifts/duration";
import { Input } from "@/components/ui/Input";
import { Textarea } from "@/components/ui/Textarea";
import { Button } from "@/components/ui/Button";

interface ShiftFormProps {
  initial?: Partial<ShiftFormData>;
  profiles?: CompensationProfile[];
  defaultProfileId?: string | null;
  onSubmit: (data: ShiftFormData) => Promise<void>;
  onDelete?: () => Promise<void>;
  submitLabel?: string;
  isActive?: boolean;
}

function toLocalDatetimeValue(iso: string): string {
  // Convert ISO string to the value expected by <input type="datetime-local">
  // Format: YYYY-MM-DDTHH:mm
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toISO(localValue: string): string {
  return new Date(localValue).toISOString();
}

export function ShiftForm({
  initial,
  profiles = [],
  defaultProfileId,
  onSubmit,
  onDelete,
  submitLabel = "Save Shift",
  isActive = false,
}: ShiftFormProps) {
  const now = new Date();

  const [startTime, setStartTime] = useState(
    initial?.start_time ? toLocalDatetimeValue(initial.start_time) : toLocalDatetimeValue(now.toISOString())
  );
  const [endTime, setEndTime] = useState(
    initial?.end_time ? toLocalDatetimeValue(initial.end_time) : ""
  );
  const [breakMinutes, setBreakMinutes] = useState(
    String(initial?.break_minutes ?? 0)
  );
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [isSpecialDay, setIsSpecialDay] = useState(initial?.is_special_day ?? false);
  const [profileId, setProfileId] = useState(
    initial?.compensation_profile_id ?? defaultProfileId ?? ""
  );
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    const breakMins = parseInt(breakMinutes, 10) || 0;
    const endIso = endTime ? toISO(endTime) : null;
    const validationError = validateShiftTimes(toISO(startTime), endIso, breakMins);
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        start_time: toISO(startTime),
        end_time: endIso ?? "",
        break_minutes: breakMins,
        notes,
        is_special_day: isSpecialDay,
        compensation_profile_id: profileId || defaultProfileId || null,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save shift.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!onDelete) return;
    setDeleting(true);
    try {
      await onDelete();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete shift.");
      setDeleting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Input
        label="Start Time"
        type="datetime-local"
        value={startTime}
        onChange={(e) => setStartTime(e.target.value)}
        required
      />

      <Input
        label={isActive ? "End Time (leave blank if still active)" : "End Time"}
        type="datetime-local"
        value={endTime}
        onChange={(e) => setEndTime(e.target.value)}
        required={!isActive}
      />

      <Input
        label="Break (minutes)"
        type="number"
        min={0}
        max={720}
        value={breakMinutes}
        onChange={(e) => setBreakMinutes(e.target.value)}
        hint="Total unpaid break time during this shift"
      />

      {/* Holiday / Shabbat toggle */}
      <button
        type="button"
        onClick={() => setIsSpecialDay((v) => !v)}
        className={[
          "flex items-center justify-between rounded-2xl border px-4 py-3.5 transition-all duration-150 text-left",
          isSpecialDay
            ? "bg-violet-50 border-violet-200"
            : "bg-gray-50 border-gray-100 hover:border-gray-200",
        ].join(" ")}
      >
        <div>
          <p className={[
            "text-sm font-semibold leading-none",
            isSpecialDay ? "text-violet-700" : "text-gray-700",
          ].join(" ")}>
            Holiday / Shabbat
          </p>
          <p className="text-xs text-gray-400 mt-1">
            {isSpecialDay
              ? "Premium holiday rates apply per your compensation profile"
              : "Mark to apply holiday / special day premium rates"}
          </p>
        </div>
        {/* Toggle pill */}
        <div className={[
          "relative flex-shrink-0 h-6 w-11 rounded-full transition-colors duration-200",
          isSpecialDay ? "bg-violet-500" : "bg-gray-200",
        ].join(" ")}>
          <span className={[
            "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform duration-200",
            isSpecialDay ? "translate-x-5" : "translate-x-0",
          ].join(" ")} />
        </div>
      </button>

      {profiles.length > 1 && (
        <div>
          <label className="text-xs font-semibold text-gray-600 mb-1.5 block">
            Compensation profile
          </label>
          <select
            value={profileId}
            onChange={(e) => setProfileId(e.target.value)}
            className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm"
          >
            {profiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.currency_code})
              </option>
            ))}
          </select>
        </div>
      )}

      <Textarea
        label="Notes (optional)"
        placeholder="Any notes about this shift…"
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        rows={2}
      />

      {error && (
        <p className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-700">
          {error}
        </p>
      )}

      <Button type="submit" loading={submitting} fullWidth size="lg">
        {submitLabel}
      </Button>

      {onDelete && !showDeleteConfirm && (
        <Button
          type="button"
          variant="ghost"
          fullWidth
          onClick={() => setShowDeleteConfirm(true)}
          className="text-red-500 hover:text-red-600 hover:bg-red-50"
        >
          Delete Shift
        </Button>
      )}

      {showDeleteConfirm && (
        <div className="rounded-2xl bg-red-50 border border-red-100 p-4 flex flex-col gap-3">
          <p className="text-sm text-red-700 font-medium text-center">
            Delete this shift? This cannot be undone.
          </p>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              fullWidth
              onClick={() => setShowDeleteConfirm(false)}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="danger"
              fullWidth
              loading={deleting}
              onClick={handleDelete}
            >
              Delete
            </Button>
          </div>
        </div>
      )}
    </form>
  );
}
