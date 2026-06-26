import { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  action?: ReactNode;
}

export function PageHeader({ title, action }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between px-5 pt-12 pb-4">
      <h1
        className="text-3xl font-bold tracking-tight"
        style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.02em" }}
      >
        {title}
      </h1>
      {action && <div>{action}</div>}
    </div>
  );
}
