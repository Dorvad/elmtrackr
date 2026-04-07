interface StatCardProps {
  label: string;
  value: string;
  sub?: string;
  accent?: boolean;
}

export function StatCard({ label, value, sub, accent = false }: StatCardProps) {
  return (
    <div
      className={[
        "rounded-2xl p-4 flex flex-col gap-1",
        accent
          ? "bg-blue-600 text-white"
          : "bg-gray-50 border border-gray-100 text-gray-800",
      ].join(" ")}
    >
      <span
        className={[
          "text-xs font-semibold uppercase tracking-wide",
          accent ? "text-blue-200" : "text-gray-500",
        ].join(" ")}
      >
        {label}
      </span>
      <span className="text-2xl font-bold leading-tight">{value}</span>
      {sub && (
        <span
          className={[
            "text-xs",
            accent ? "text-blue-200" : "text-gray-500",
          ].join(" ")}
        >
          {sub}
        </span>
      )}
    </div>
  );
}
