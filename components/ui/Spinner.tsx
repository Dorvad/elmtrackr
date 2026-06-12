export function Spinner({ size = "md" }: { size?: "sm" | "md" | "lg" }) {
  const sizeClasses = { sm: "h-4 w-4", md: "h-6 w-6", lg: "h-10 w-10" };
  return (
    <span
      className={[
        "inline-block animate-spin rounded-full border-2 border-[var(--au-hair)] border-t-[var(--au-indigo)]",
        sizeClasses[size],
      ].join(" ")}
    />
  );
}

export function PageSpinner() {
  return (
    <div className="flex h-48 items-center justify-center">
      <Spinner size="lg" />
    </div>
  );
}
