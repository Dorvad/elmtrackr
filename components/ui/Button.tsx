"use client";

import { ButtonHTMLAttributes, forwardRef } from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  fullWidth?: boolean;
}

const variantClasses: Record<Variant, string> = {
  primary: [
    "text-white shadow-[0_14px_26px_-10px_rgba(91,77,242,0.7)]",
    "hover:shadow-[0_16px_30px_-10px_rgba(91,77,242,0.85)]",
    "active:scale-[0.98]",
    "disabled:opacity-50 disabled:shadow-none",
  ].join(" "),
  secondary: [
    "bg-white text-[var(--au-indigo)] border border-[var(--au-indigo)] border-opacity-30",
    "hover:bg-[var(--au-surface-sub)] hover:border-opacity-60",
    "active:scale-[0.98]",
    "disabled:bg-gray-50 disabled:text-gray-300 disabled:border-gray-100",
  ].join(" "),
  danger: [
    "bg-red-500 text-white shadow-md shadow-red-500/20",
    "hover:bg-red-600",
    "active:scale-[0.98]",
    "disabled:bg-red-200 disabled:shadow-none",
  ].join(" "),
  ghost: [
    "bg-transparent text-[var(--au-ink-2)]",
    "hover:bg-[var(--au-surface-sub)] hover:text-[var(--au-ink)]",
    "active:scale-[0.98]",
    "disabled:text-[var(--au-faint)]",
  ].join(" "),
};

const sizeClasses: Record<Size, string> = {
  sm: "px-3 py-1.5 text-sm rounded-2xl",
  md: "px-4 py-2.5 text-base rounded-2xl",
  lg: "px-6 py-3.5 text-base rounded-[18px]",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = "primary",
      size = "md",
      loading = false,
      fullWidth = false,
      disabled,
      children,
      className = "",
      style,
      ...props
    },
    ref
  ) => {
    const isPrimary = variant === "primary";
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        style={
          isPrimary
            ? { background: "var(--au-grad)", ...style }
            : style
        }
        className={[
          "inline-flex items-center justify-center gap-2 font-semibold",
          "transition-all duration-150 focus-visible:outline-none",
          "focus-visible:ring-2 focus-visible:ring-[var(--au-indigo)] focus-visible:ring-offset-2",
          "disabled:cursor-not-allowed select-none",
          variantClasses[variant],
          sizeClasses[size],
          fullWidth ? "w-full" : "",
          className,
        ]
          .filter(Boolean)
          .join(" ")}
        {...props}
      >
        {loading && (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
        )}
        {children}
      </button>
    );
  }
);

Button.displayName = "Button";
