"use client";

import { InputHTMLAttributes, forwardRef } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, className = "", id, ...props }, ref) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, "-");

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={inputId} className="text-sm font-semibold" style={{ color: "var(--au-ink-2)" }}>
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={[
            "w-full rounded-xl border px-4 py-2.5 text-sm",
            "placeholder:text-[var(--au-faint)]",
            "focus:outline-none focus:ring-2 focus:border-transparent",
            "transition-all duration-150",
            error
              ? "border-red-300 focus:ring-red-400 bg-red-50"
              : "border-[color:var(--au-hair)] hover:border-[color:var(--au-faint)] focus:ring-indigo-500",
            "disabled:opacity-50 disabled:cursor-not-allowed",
            className,
          ]
            .filter(Boolean)
            .join(" ")}
          style={{
            color: "var(--au-ink)",
            backgroundColor: "var(--au-surface-sub)",
          }}
          {...props}
        />
        {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
        {hint && !error && <p className="text-xs" style={{ color: "var(--au-faint)" }}>{hint}</p>}
      </div>
    );
  }
);

Input.displayName = "Input";
