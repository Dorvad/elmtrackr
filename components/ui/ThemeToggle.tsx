"use client";

import { useTheme, type Theme } from "@/hooks/useTheme";

const OPTIONS: { value: Theme; label: string }[] = [
  { value: "system", label: "System default" },
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
];

export function ThemeToggle() {
  const { theme, setTheme, mounted } = useTheme();

  if (!mounted) {
    return (
      <div className="h-11 w-full rounded-xl animate-pulse" style={{ background: "var(--au-surface-sub)" }} />
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor="theme-select" className="text-sm font-semibold" style={{ color: "var(--au-ink-2)" }}>
        Theme
      </label>
      <select
        id="theme-select"
        value={theme}
        onChange={(e) => setTheme(e.target.value as Theme)}
        className="w-full rounded-xl border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        style={{
          color: "var(--au-ink)",
          backgroundColor: "var(--au-surface-sub)",
          borderColor: "var(--au-hair)",
        }}
      >
        {OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}
