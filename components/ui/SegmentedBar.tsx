"use client";

interface Segment {
  label: string;
  value: number;
  color: string; // bg-* tailwind class or arbitrary css var, e.g. bg-[var(--au-indigo)]
  dotColor: string; // inline dot color, e.g. var(--au-indigo)
}

interface SegmentedBarProps {
  segments: Segment[];
  className?: string;
}

export function SegmentedBar({ segments, className = "" }: SegmentedBarProps) {
  const total = segments.reduce((s, seg) => s + seg.value, 0);
  if (total === 0) {
    return (
      <div
        className={`h-3 rounded-full overflow-hidden ${className}`}
        style={{ background: "var(--au-surface-sub)" }}
      />
    );
  }

  return (
    <div className={className}>
      {/* Bar */}
      <div className="flex h-3 rounded-full overflow-hidden gap-px">
        {segments
          .filter((s) => s.value > 0)
          .map((seg, i) => {
            const pct = (seg.value / total) * 100;
            return (
              <div
                key={i}
                className={`animate-bar-expand ${seg.color}`}
                style={{
                  width: `${pct}%`,
                  animationDelay: `${i * 0.08}s`,
                }}
              />
            );
          })}
      </div>
    </div>
  );
}

export function SegmentLegend({ segments }: { segments: Segment[] }) {
  const total = segments.reduce((s, seg) => s + seg.value, 0);
  return (
    <div className="flex flex-col gap-2 mt-3">
      {segments.map((seg, i) => {
        const pct = total > 0 ? ((seg.value / total) * 100).toFixed(0) : "0";
        return (
          <div key={i} className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span
                className="h-2 w-2 rounded-full flex-shrink-0"
                style={{ backgroundColor: seg.dotColor }}
              />
              <span className="text-sm" style={{ color: "var(--au-ink-2)" }}>{seg.label}</span>
              <span className="text-xs" style={{ color: "var(--au-faint)" }}>{pct}%</span>
            </div>
            <span className="text-sm font-semibold" style={{ color: "var(--au-ink)" }}>
              {(seg.value / 60).toFixed(1)}h
            </span>
          </div>
        );
      })}
    </div>
  );
}
