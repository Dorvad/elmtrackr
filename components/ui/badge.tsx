import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "border-transparent text-white",
        outline: "border-current bg-transparent",
        subtle: "border-transparent",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, style, ...props }: BadgeProps) {
  const variantStyle: React.CSSProperties =
    variant === "outline"
      ? { color: "var(--au-indigo)", borderColor: "var(--au-indigo)" }
      : variant === "subtle"
      ? { background: "rgba(91,77,242,0.10)", color: "var(--au-indigo)" }
      : { background: "var(--au-grad)" };

  return (
    <div
      className={cn(badgeVariants({ variant }), className)}
      style={{ ...variantStyle, ...style }}
      {...props}
    />
  );
}

export { Badge, badgeVariants };
