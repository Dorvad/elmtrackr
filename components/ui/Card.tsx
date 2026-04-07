import { HTMLAttributes } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padding?: "sm" | "md" | "lg" | "none";
  glass?: boolean;
}

const paddingClasses = {
  none: "",
  sm: "p-3",
  md: "p-4",
  lg: "p-5",
};

export function Card({
  children,
  padding = "md",
  glass = false,
  className = "",
  ...props
}: CardProps) {
  return (
    <div
      className={[
        "rounded-2xl border border-gray-100 shadow-sm",
        glass ? "glass" : "bg-white",
        paddingClasses[padding],
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    >
      {children}
    </div>
  );
}
