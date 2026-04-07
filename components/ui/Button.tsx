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
    "bg-indigo-600 text-white shadow-md shadow-indigo-500/25",
    "hover:bg-indigo-700 hover:shadow-lg hover:shadow-indigo-500/30",
    "active:bg-indigo-800 active:scale-[0.98]",
    "disabled:bg-indigo-300 disabled:shadow-none",
  ].join(" "),
  secondary: [
    "bg-gray-100 text-gray-700 border border-gray-200",
    "hover:bg-gray-150 hover:border-gray-300",
    "active:bg-gray-200 active:scale-[0.98]",
    "disabled:bg-gray-50 disabled:text-gray-300 disabled:border-gray-100",
  ].join(" "),
  danger: [
    "bg-red-500 text-white shadow-md shadow-red-500/20",
    "hover:bg-red-600",
    "active:bg-red-700 active:scale-[0.98]",
    "disabled:bg-red-200 disabled:shadow-none",
  ].join(" "),
  ghost: [
    "bg-transparent text-gray-500",
    "hover:bg-gray-100 hover:text-gray-700",
    "active:bg-gray-200 active:scale-[0.98]",
    "disabled:text-gray-300",
  ].join(" "),
};

const sizeClasses: Record<Size, string> = {
  sm: "px-3 py-1.5 text-sm rounded-xl",
  md: "px-4 py-2.5 text-base rounded-xl",
  lg: "px-6 py-3.5 text-base rounded-2xl",
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
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={[
          "inline-flex items-center justify-center gap-2 font-semibold",
          "transition-all duration-150 focus-visible:outline-none",
          "focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2",
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
