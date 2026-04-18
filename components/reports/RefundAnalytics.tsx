"use client";

import type { RefundClaim, Shift } from "@/types";
import type { UserSettings } from "@/types";
import { calculateShiftPay, formatCurrency } from "@/lib/shifts/payroll";

interface Props {
  claims: RefundClaim[];
  shifts: Shift[];
  settings?: UserSettings | null;
}

const PROVIDER_STYLES: Record<string, { bar: string; text: string; bg: string; border: string; dot: string }> = {
  Lime:  { bar: "bg-emerald-500", text: "text-emerald-700", bg: "bg-emerald-50", border: "border-emerald-100", dot: "#10b981" },
  Dott:  { bar: "bg-orange-500",  text: "text-orange-700",  bg: "bg-orange-50",  border: "border-orange-100",  dot: "#f97316" },
  Other: { bar: "bg-slate-400",   text: "text-slate-600",   bg: "bg-slate-50",   border: "border-slate-100",   dot: "#94a3b8" },
};

function providerStyle(name: string) {
  return PROVIDER_STYLES[name] ?? { bar: "bg-indigo-400", text: "text-indigo-700", bg: "bg-indigo-50", border: "border-indigo-100", dot: "#818cf8" };
}

export function RefundAnalytics({ claims, shifts, settings }: Props) {
  if (claims.length === 0) return null;

  // ── Core numbers ────────────────────────────────────────────────────────
  const totalAmount = claims.reduce((s, c) => s + c.amount, 0);
  const avgAmount   = totalAmount / claims.length;
  const maxClaim    = Math.max(...claims.map((c) => c.amount));

  // ── Provider breakdown ───────────────────────────────────────────────────
  const providerMap = new Map<string, { count: number; total: number }>();
  for (const c of claims) {
    const p = providerMap.get(c.provider) ?? { count: 0, total: 0 };
    providerMap.set(c.provider, { count: p.count + 1, total: p.total + c.amount });
  }
  const providerStats = [...providerMap.entries()]
    .map(([name, s]) => ({ name, ...s }))
    .sort((a, b) => b.total - a.total);

  // ── Monthly breakdown ────────────────────────────────────────────────────
  const monthlyMap = new Map<string, number>();
  for (const c of claims) {
    const d   = new Date(c.ride_at);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    monthlyMap.set(key, (monthlyMap.get(key) ?? 0) + c.amount);
  }
  const activeMonths = monthlyMap.size;

  let bestKey = "";
  let bestAmt = 0;
  for (const [k, v] of monthlyMap) {
    if (v > bestAmt) { bestAmt = v; bestKey = k; }
  }
  const bestLabel = bestKey
    ? new Date(Number(bestKey.split("-")[0]), Number(bestKey.split("-")[1]) - 1, 1)
        .toLocaleString("default", { month: "short", year: "numeric" })
    : null;

  // ── 6-month trend ────────────────────────────────────────────────────────
  const now = new Date();
  const trend = Array.from({ length: 6 }, (_, i) => {
    const d   = new Date(now.getFullYear(), now.getMonth() - (5 - i), 1);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    return {
      key,
      label:     d.toLocaleString("default", { month: "short" }),
      amount:    monthlyMap.get(key) ?? 0,
      isCurrent: i === 5,
    };
  });
  const maxTrend = Math.max(...trend.map((m) => m.amount), 1);

  // ── Salary + refunds ─────────────────────────────────────────────────────
  let totalGross: number | null = null;
  if (settings?.hourly_rate) {
    totalGross = shifts
      .filter((s) => s.end_time)
      .reduce((sum, s) => sum + (calculateShiftPay(s, settings)?.total_gross ?? 0), 0);
  }

  return (
    <div className="flex flex-col gap-4">

      {/* ── Hero card ─────────────────────────────────────────────────────── */}
      <div className="rounded-2xl bg-gradient-to-br from-indigo-600 to-violet-700 p-5 shadow-lg shadow-indigo-500/20 animate-fade-in-up">
        <p className="text-xs font-bold text-indigo-200 uppercase tracking-widest">Total Refunded</p>
        <p className="text-4xl font-extrabold text-white mt-1 tracking-tight">
          {formatCurrency(totalAmount)}
        </p>
        <p className="text-sm text-indigo-200 mt-1 font-medium">
          {claims.length} ride{claims.length !== 1 ? "s" : ""} · {activeMonths} month{activeMonths !== 1 ? "s" : ""}
        </p>

        {totalGross != null && totalGross > 0 && (
          <>
            <div className="h-px bg-white/20 my-3.5" />
            <p className="text-[10px] font-bold text-indigo-200 uppercase tracking-widest mb-2">
              Total compensation (salary + refunds)
            </p>
            <p className="text-2xl font-extrabold text-white tracking-tight mb-3">
              {formatCurrency(totalGross + totalAmount)}
            </p>
            <div className="flex gap-2">
              <div className="flex-1 rounded-xl bg-white/10 px-3 py-2.5 text-center">
                <p className="text-[10px] text-indigo-200 font-bold uppercase tracking-wide">Salary</p>
                <p className="text-sm font-extrabold text-white mt-0.5">{formatCurrency(totalGross)}</p>
              </div>
              <div className="flex-1 rounded-xl bg-white/10 px-3 py-2.5 text-center">
                <p className="text-[10px] text-indigo-200 font-bold uppercase tracking-wide">Refunds</p>
                <p className="text-sm font-extrabold text-white mt-0.5">{formatCurrency(totalAmount)}</p>
              </div>
              <div className="flex-1 rounded-xl bg-white/15 px-3 py-2.5 text-center border border-white/20">
                <p className="text-[10px] text-indigo-100 font-bold uppercase tracking-wide">Refund %</p>
                <p className="text-sm font-extrabold text-white mt-0.5">
                  {((totalAmount / (totalGross + totalAmount)) * 100).toFixed(1)}%
                </p>
              </div>
            </div>
          </>
        )}
      </div>

      {/* ── Provider distribution ─────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-1">
        <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">By Provider</p>
        <div className="flex flex-col gap-4">
          {providerStats.map(({ name, count, total }) => {
            const s   = providerStyle(name);
            const pct = (total / totalAmount) * 100;
            return (
              <div key={name}>
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2">
                    <span className="h-2.5 w-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: s.dot }} />
                    <span className="text-sm font-bold text-gray-800">{name}</span>
                    <span className="text-xs text-gray-400 font-medium">
                      {count} ride{count !== 1 ? "s" : ""}
                    </span>
                  </div>
                  <div className="text-right">
                    <span className={`text-sm font-extrabold ${s.text}`}>{formatCurrency(total)}</span>
                    <span className="text-xs text-gray-400 ml-1.5">{pct.toFixed(0)}%</span>
                  </div>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full animate-bar-expand ${s.bar}`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Stats grid ───────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 gap-3">
        {[
          {
            icon: "⌀", label: "Avg per ride",
            value: formatCurrency(avgAmount), sub: "per claim",
            bg: "bg-emerald-50 border-emerald-100", text: "text-emerald-700", sub2: "text-emerald-400",
          },
          {
            icon: "↑", label: "Largest ride",
            value: formatCurrency(maxClaim), sub: "single claim",
            bg: "bg-indigo-50 border-indigo-100", text: "text-indigo-700", sub2: "text-indigo-400",
          },
          {
            icon: "★", label: "Best month",
            value: formatCurrency(bestAmt), sub: bestLabel ?? "—",
            bg: "bg-orange-50 border-orange-100", text: "text-orange-700", sub2: "text-orange-400",
          },
          {
            icon: "#", label: "Active months",
            value: String(activeMonths), sub: `${claims.length} rides total`,
            bg: "bg-violet-50 border-violet-100", text: "text-violet-700", sub2: "text-violet-400",
          },
        ].map((item, i) => (
          <div
            key={item.label}
            className={`rounded-2xl border p-3.5 shadow-sm flex flex-col gap-1 animate-fade-in-up ${item.bg} stagger-${i + 1}`}
          >
            <span className={`text-[10px] font-bold uppercase tracking-widest ${item.sub2}`}>
              {item.label}
            </span>
            <span className={`text-xl font-extrabold tracking-tight leading-tight ${item.text}`}>
              {item.value}
            </span>
            <span className={`text-[10px] font-medium ${item.sub2}`}>{item.sub}</span>
          </div>
        ))}
      </div>

      {/* ── Monthly trend ────────────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-3">
        <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">Monthly Trend</p>
        <div className="flex items-end gap-2" style={{ height: "72px" }}>
          {trend.map((m) => (
            <div key={m.key} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
              <div
                className={`w-full rounded-t-md transition-all ${m.isCurrent ? "bg-indigo-500" : "bg-indigo-200"}`}
                style={{
                  height: m.amount > 0 ? `${Math.max((m.amount / maxTrend) * 56, 4)}px` : "3px",
                  opacity: m.amount > 0 ? 1 : 0.35,
                }}
              />
              <span className={`text-[9px] font-semibold ${m.isCurrent ? "text-indigo-600" : "text-gray-400"}`}>
                {m.label}
              </span>
            </div>
          ))}
        </div>
        {trend.some((m) => m.amount > 0) && (
          <div className="flex justify-between mt-3 pt-2.5 border-t border-gray-50">
            <span className="text-[10px] text-gray-400">6-month window</span>
            <span className="text-[10px] font-semibold text-indigo-500">
              {formatCurrency(trend.reduce((s, m) => s + m.amount, 0))} total
            </span>
          </div>
        )}
      </div>

    </div>
  );
}
