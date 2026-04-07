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
          <label htmlFor={inputId} className="text-sm font-semibold text-gray-600">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={[
            "w-full rounded-xl border px-4 py-2.5 text-sm text-gray-900 bg-gray-50",
            "placeholder:text-gray-400",
            "focus:outline-none focus:ring-2 focus:border-transparent focus:bg-white",
            "transition-all duration-150",
            error
              ? "border-red-300 focus:ring-red-400 bg-red-50"
              : "border-gray-200 hover:border-gray-300 focus:ring-indigo-500",
            "disabled:opacity-50 disabled:cursor-not-allowed",
            className,
          ]
            .filter(Boolean)
            .join(" ")}
          {...props}
        />
        {error && <p className="text-xs text-red-500 font-medium">{error}</p>}
        {hint && !error && <p className="text-xs text-gray-400">{hint}</p>}
      </div>
    );
  }
);

Input.displayName = "Input";
