interface StatCardProps {
  label: string;
  value: string;
  sub?: string;
  variant?: "default" | "primary" | "overtime" | "weekend" | "active";
  stagger?: 1 | 2 | 3 | 4;
}

const variantStyles = {
  default: {
    wrap: "bg-white border border-white/80 au-card",
    label: "text-[var(--au-faint)]",
    value: "text-[var(--au-ink)]",
    sub: "text-[var(--au-faint)]",
  },
  primary: {
    wrap: "border-0",
    label: "text-white/80",
    value: "text-white",
    sub: "text-white/70",
  },
  overtime: {
    wrap: "border border-white/80 au-card",
    label: "",
    value: "",
    sub: "",
  },
  weekend: {
    wrap: "border border-white/80 au-card",
    label: "",
    value: "",
    sub: "",
  },
  active: {
    wrap: "bg-emerald-50 border border-emerald-100 au-card",
    label: "text-emerald-500",
    value: "text-emerald-800",
    sub: "text-emerald-400",
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

  if (variant === "primary") {
    return (
      <div
        className={[
          "rounded-3xl p-4 flex flex-col gap-1 shadow-[0_16px_30px_-14px_rgba(91,77,242,0.55)] animate-fade-in-up",
          s.wrap,
          stagger ? `stagger-${stagger}` : "",
        ].join(" ")}
        style={{ background: "var(--au-grad)" }}
      >
        <span className={`text-xs font-bold uppercase tracking-wider ${s.label}`}>{label}</span>
        <span className={`text-2xl font-extrabold leading-tight tracking-tight ${s.value}`}>{value}</span>
        {sub && <span className={`text-xs font-medium ${s.sub}`}>{sub}</span>}
      </div>
    );
  }

  if (variant === "overtime") {
    return (
      <div
        className={[
          "rounded-3xl p-4 flex flex-col gap-1 animate-fade-in-up",
          s.wrap,
          stagger ? `stagger-${stagger}` : "",
        ].join(" ")}
        style={{ background: "var(--au-overtime-bg)" }}
      >
        <span className="text-xs font-bold uppercase tracking-wider" style={{ color: "var(--au-overtime-ink)" }}>{label}</span>
        <span className="text-2xl font-extrabold leading-tight tracking-tight" style={{ color: "var(--au-peach-deep)" }}>{value}</span>
        {sub && <span className="text-xs font-medium" style={{ color: "var(--au-overtime-ink)" }}>{sub}</span>}
      </div>
    );
  }

  if (variant === "weekend") {
    return (
      <div
        className={[
          "rounded-3xl p-4 flex flex-col gap-1 animate-fade-in-up",
          s.wrap,
          stagger ? `stagger-${stagger}` : "",
        ].join(" ")}
        style={{ background: "var(--au-weekend-bg)" }}
      >
        <span className="text-xs font-bold uppercase tracking-wider" style={{ color: "var(--au-plum)" }}>{label}</span>
        <span className="text-2xl font-extrabold leading-tight tracking-tight" style={{ color: "var(--au-plum)" }}>{value}</span>
        {sub && <span className="text-xs font-medium" style={{ color: "var(--au-plum)" }}>{sub}</span>}
      </div>
    );
  }

  return (
    <div
      className={[
        "rounded-3xl p-4 flex flex-col gap-1 animate-fade-in-up",
        s.wrap,
        stagger ? `stagger-${stagger}` : "",
      ].join(" ")}
    >
      <span className={`text-xs font-bold uppercase tracking-wider ${s.label}`}>{label}</span>
      <span className={`text-2xl font-extrabold leading-tight tracking-tight ${s.value}`}>{value}</span>
      {sub && <span className={`text-xs font-medium ${s.sub}`}>{sub}</span>}
    </div>
  );
}
