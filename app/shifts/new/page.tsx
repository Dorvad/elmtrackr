"use client";

export const dynamic = "force-dynamic";

import { useRouter } from "next/navigation";
import { useShifts } from "@/hooks/useShifts";
import { useToast } from "@/components/ui/Toast";
import { ShiftForm } from "@/components/shifts/ShiftForm";
import { BottomNav } from "@/components/layout/BottomNav";
import type { ShiftFormData } from "@/types";

export default function NewShiftPage() {
  const router = useRouter();
  const { createShift } = useShifts();
  const { toast } = useToast();

  async function handleSubmit(data: ShiftFormData) {
    await createShift({
      start_time: data.start_time,
      end_time: data.end_time || null,
      break_minutes: data.break_minutes,
      notes: data.notes || null,
    });
    toast("Shift saved", "success");
    router.push("/shifts");
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
        <h1 className="text-lg font-bold text-gray-900">New Shift</h1>
      </div>

      <div className="max-w-md mx-auto px-4 pt-6">
        <ShiftForm
          onSubmit={handleSubmit}
          submitLabel="Create Shift"
        />
      </div>

      <BottomNav />
    </div>
  );
}
