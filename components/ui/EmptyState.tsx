interface EmptyStateProps {
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
      <div className="mb-3 text-4xl" aria-hidden="true">📋</div>
      <h3 className="text-base font-semibold" style={{ color: "var(--au-ink)" }}>{title}</h3>
      {description && (
        <p className="mt-1 text-sm max-w-xs" style={{ color: "var(--au-ink-2)" }}>{description}</p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
