import { Shift, RefundAction } from "@/types";

export interface RefundEligibility {
  eligible: boolean;
  reasons: string[];
}

// Local-time helpers — eligibility is based on when the user experienced the shift
function localDate(iso: string): Date {
  return new Date(iso);
}

function localHour(iso: string): number {
  return localDate(iso).getHours();
}

function localMinute(iso: string): number {
  return localDate(iso).getMinutes();
}

function localDay(iso: string): number {
  return localDate(iso).getDay(); // 0=Sun, 1=Mon, ..., 5=Fri, 6=Sat
}

// Eligibility for the ride TO work — mirrors from-work logic but checks start_time
export function checkToWorkEligibility(shift: Shift): RefundEligibility {
  const reasons: string[] = [];
  const startDay  = localDay(shift.start_time);
  const startHour = localHour(shift.start_time);
  const startMin  = localMinute(shift.start_time);

  const isLateNight    = startHour === 23 && startMin >= 30;
  const isEarlyMorning = startHour < 10;
  if (isLateNight || isEarlyMorning) {
    reasons.push("Late night / early morning start");
  }

  if (startDay === 5 && startHour >= 17) {
    reasons.push("Friday night shift");
  }

  if (startDay === 6) {
    reasons.push("Saturday shift");
  }

  if (shift.is_special_day) {
    reasons.push("Holiday shift");
  }

  return { eligible: reasons.length > 0, reasons };
}

export function checkRefundEligibility(shift: Shift): RefundEligibility {
  if (!shift.end_time) return { eligible: false, reasons: [] };

  const reasons: string[] = [];
  const endDay = localDay(shift.end_time);
  const endHour = localHour(shift.end_time);
  const endMin = localMinute(shift.end_time);

  // Ends between 23:30–23:59 or 00:00–09:59 (late night / early morning)
  const isLateNight = endHour === 23 && endMin >= 30;
  const isEarlyMorning = endHour < 10;
  if (isLateNight || isEarlyMorning) {
    reasons.push("Late night / early morning shift");
  }

  // Friday night: ends after 17:00 on Friday (day 5)
  if (endDay === 5 && endHour >= 17) {
    reasons.push("Friday night shift");
  }

  // Saturday shift: ends on Saturday (day 6)
  if (endDay === 6) {
    reasons.push("Saturday shift");
  }

  // Holiday / special day flag
  if (shift.is_special_day) {
    reasons.push("Holiday shift");
  }

  return { eligible: reasons.length > 0, reasons };
}

export function getRefundStatus(shift: Shift): {
  eligible: boolean;
  action: RefundAction;
  label: string;
  color: string;
} {
  const { eligible } = checkRefundEligibility(shift);

  if (!eligible) {
    return { eligible: false, action: null, label: "", color: "" };
  }

  switch (shift.refund_action) {
    case "submitted":
      return { eligible: true, action: "submitted", label: "Submitted", color: "emerald" };
    case "no_ride_taken":
      return { eligible: true, action: "no_ride_taken", label: "No ride taken", color: "gray" };
    case "remind_later":
      return { eligible: true, action: "remind_later", label: "Remind later", color: "amber" };
    default:
      return { eligible: true, action: null, label: "Pending", color: "orange" };
  }
}

export function isRefundUnresolved(shift: Shift): boolean {
  if (!shift.end_time) return false;
  const { eligible } = checkRefundEligibility(shift);
  return eligible && shift.refund_action == null;
}

export function countUnresolvedRefunds(shifts: Shift[]): number {
  return shifts.filter(isRefundUnresolved).length;
}

// Returns the month key "YYYY-MM" for a shift's end time
export function shiftMonthKey(shift: Shift): string {
  const d = localDate(shift.end_time!);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

// Returns all months (as "YYYY-MM") that have at least one eligible shift
export function eligibleMonths(shifts: Shift[]): string[] {
  const seen = new Set<string>();
  for (const s of shifts) {
    if (s.end_time && checkRefundEligibility(s).eligible) {
      seen.add(shiftMonthKey(s));
    }
  }
  return Array.from(seen).sort().reverse();
}
