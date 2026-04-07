interface StatCardProps {
  label: string;
  value: string;
  sub?: string;
  /** Visual style variant */
  variant?: "default" | "primary" | "overtime" | "weekend" | "active";
  /** Optional stagger index for entry animation */
  stagger?: 1 | 2 | 3 | 4;
}

const variantStyles = {
  default: {
    wrap: "bg-white border border-gray-100 text-gray-800",
    label: "text-gray-400",
    value: "text-gray-900",
    sub: "text-gray-400",
    bar: "bg-indigo-100",
  },
  primary: {
    wrap: "bg-gradient-to-br from-indigo-600 to-violet-700 text-white border-0",
    label: "text-indigo-200",
    value: "text-white",
    sub: "text-indigo-200",
    bar: "bg-white/20",
  },
  overtime: {
    wrap: "bg-amber-50 border border-amber-100 text-amber-900",
    label: "text-amber-400",
    value: "text-amber-800",
    sub: "text-amber-400",
    bar: "bg-amber-200",
  },
  weekend: {
    wrap: "bg-violet-50 border border-violet-100 text-violet-900",
    label: "text-violet-400",
    value: "text-violet-800",
    sub: "text-violet-400",
    bar: "bg-violet-200",
  },
  active: {
    wrap: "bg-emerald-50 border border-emerald-100 text-emerald-900",
    label: "text-emerald-400",
    value: "text-emerald-800",
    sub: "text-emerald-400",
    bar: "bg-emerald-200",
  },
};

export function StatCard({
  label,
  value,
  sub,
  variant = "default",
  stagger,
}: StatCardProps) {
  const s = variantStyles[variant];

  return (
    <div
      className={[
        "rounded-2xl p-4 flex flex-col gap-1 shadow-sm animate-fade-in-up",
        s.wrap,
        stagger ? `stagger-${stagger}` : "",
      ].join(" ")}
    >
      <span className={`text-xs font-semibold uppercase tracking-wider ${s.label}`}>
        {label}
      </span>
      <span className={`text-2xl font-extrabold leading-tight tracking-tight ${s.value}`}>
        {value}
      </span>
      {sub && (
        <span className={`text-xs font-medium ${s.sub}`}>{sub}</span>
      )}
    </div>
  );
}
