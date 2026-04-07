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
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">New Shift</h1>
      </div>

      <div className="max-w-md mx-auto px-4 animate-fade-in-up stagger-1">
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
          <ShiftForm onSubmit={handleSubmit} submitLabel="Create Shift" />
        </div>
      </div>

      <BottomNav />
    </div>
  );
}
