"use client";

export const dynamic = "force-dynamic";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useShifts } from "@/hooks/useShifts";
import { useToast } from "@/components/ui/Toast";
import { ShiftForm } from "@/components/shifts/ShiftForm";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import type { ShiftFormData } from "@/types";

export default function EditShiftPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const { shifts, loading, updateShift, deleteShift } = useShifts();
  const { toast } = useToast();

  const shift = shifts.find((s) => s.id === id);

  async function handleSubmit(data: ShiftFormData) {
    await updateShift(id, {
      start_time: data.start_time,
      end_time: data.end_time || null,
      break_minutes: data.break_minutes,
      notes: data.notes || null,
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
      <div className="min-h-screen bg-gray-50 pb-24">
        <PageSpinner />
      </div>
    );
  }

  if (!shift) {
    return (
      <div className="min-h-screen bg-gray-50 pb-24 flex items-center justify-center">
        <div className="text-center px-6">
          <p className="text-gray-500 text-sm">Shift not found.</p>
          <button
            onClick={() => router.push("/shifts")}
            className="mt-3 text-blue-600 text-sm font-medium hover:underline"
          >
            Back to shifts
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 px-4 py-3 flex items-center gap-3">
        <button
          onClick={() => router.back()}
          className="p-1 -ml-1 rounded-lg hover:bg-gray-100 transition-colors"
        >
          <svg className="h-5 w-5 text-gray-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-lg font-bold text-gray-900">Edit Shift</h1>
      </div>

      <div className="max-w-md mx-auto px-4 pt-6">
        <ShiftForm
          initial={{
            start_time: shift.start_time,
            end_time: shift.end_time ?? "",
            break_minutes: shift.break_minutes,
            notes: shift.notes ?? "",
          }}
          onSubmit={handleSubmit}
          onDelete={handleDelete}
          submitLabel="Update Shift"
          isActive={shift.end_time === null}
        />
      </div>

      <BottomNav />
    </div>
  );
}
