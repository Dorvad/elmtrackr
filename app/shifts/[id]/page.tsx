"use client";

export const dynamic = "force-dynamic";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { ShiftForm } from "@/components/shifts/ShiftForm";
import { RefundSection } from "@/components/shifts/RefundSection";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import type { Shift, ShiftFormData } from "@/types";

export default function EditShiftPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const { shifts, loading, updateShift, deleteShift } = useShifts();
  const { settings } = useSettings();
  const { toast } = useToast();

  async function handleRefundAction(action: Shift["refund_action"]) {
    await updateShift(id, { refund_action: action });
  }

  const shift = shifts.find((s) => s.id === id);

  async function handleSubmit(data: ShiftFormData) {
    await updateShift(id, {
      start_time: data.start_time,
      end_time: data.end_time || null,
      break_minutes: data.break_minutes,
      notes: data.notes || null,
      is_special_day: data.is_special_day,
    });
    toast("Shift updated", "success");
    router.push("/shifts");
  }

  async function handleDelete() {
    await deleteShift(id);
    toast("Shift deleted", "success");
    router.push("/shifts");
  }

  if (loading) {
    return (
      <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
        <PageSpinner />
      </div>
    );
  }

  if (!shift) {
    return (
      <div className="min-h-screen pb-28 flex items-center justify-center" style={{ background: "var(--color-surface)" }}>
        <div className="text-center px-6">
          <p className="text-gray-400 text-sm font-medium">Shift not found.</p>
          <button
            onClick={() => router.push("/shifts")}
            className="mt-3 text-indigo-600 text-sm font-bold hover:underline"
          >
            Back to shifts
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      <div className="px-4 pt-12 pb-4 flex items-center gap-3 animate-fade-in">
        <button
          onClick={() => router.back()}
          className="h-9 w-9 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center justify-center text-gray-400 hover:text-gray-600 transition-all"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Edit Shift</h1>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4 animate-fade-in-up stagger-1">
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
          <ShiftForm
            initial={{
              start_time: shift.start_time,
              end_time: shift.end_time ?? "",
              break_minutes: shift.break_minutes,
              notes: shift.notes ?? "",
              is_special_day: shift.is_special_day ?? false,
            }}
            onSubmit={handleSubmit}
            onDelete={handleDelete}
            submitLabel="Update Shift"
            isActive={shift.end_time === null}
          />
        </div>
        {settings?.features_travel_refunds && (
          <RefundSection shift={shift} onActionChange={handleRefundAction} />
        )}
      </div>

      <BottomNav />
    </div>
  );
}
