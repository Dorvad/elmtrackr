import { HTMLAttributes } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padding?: "sm" | "md" | "lg" | "none";
  glass?: boolean;
  glow?: boolean;
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
  glow = false,
  className = "",
  ...props
}: CardProps) {
  return (
    <div
      className={[
        "rounded-3xl border border-white/80",
        glass ? "glass" : "bg-white",
        glow ? "au-card-glow" : "au-card",
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
