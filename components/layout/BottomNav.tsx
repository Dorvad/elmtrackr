"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { shouldShowBottomNav } from "@/lib/navigation";

const navItems = [
  { href: "/", label: "Home", icon: HomeIcon },
  { href: "/shifts", label: "Shifts", icon: ListIcon },
  { href: "/reports", label: "Reports", icon: ChartIcon },
  { href: "/settings", label: "Settings", icon: SettingsIcon },
];

export function BottomNav() {
  const pathname = usePathname();

  if (!shouldShowBottomNav(pathname)) {
    return null;
  }

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 safe-area-pb" aria-label="Main navigation">
      <div
        className="au-nav-bar border-t shadow-[0_-4px_24px_rgba(0,0,0,0.25)]"
        style={{
          backdropFilter: "blur(22px)",
          WebkitBackdropFilter: "blur(22px)",
          borderTopColor: "var(--au-hair)",
          background: "var(--au-surface)",
        }}
      >
        <div className="flex max-w-md mx-auto px-2">
          {navItems.map(({ href, label, icon: Icon }) => {
            const active =
              href === "/" ? pathname === "/" : pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                aria-current={active ? "page" : undefined}
                className="flex flex-1 flex-col items-center gap-1 py-2.5 relative"
              >
                {/* Gradient pill behind active icon */}
                <span
                  className="flex items-center justify-center w-12 h-8 rounded-[13px] transition-all duration-250"
                  style={
                    active
                      ? {
                          background: "var(--au-grad)",
                          boxShadow: "0 8px 18px -8px rgba(91,77,242,0.6)",
                        }
                      : { background: "transparent" }
                  }
                >
                  <Icon active={active} />
                </span>
                <span
                  className="text-[10.5px] font-semibold transition-colors duration-200"
                  style={{ color: active ? "var(--au-indigo)" : "var(--au-ink-2)" }}
                >
                  {label}
                </span>
              </Link>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "var(--au-ink-2)"} strokeWidth={active ? 2.2 : 2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 10.5 12 3l9 7.5M5.5 9.5V20a1 1 0 0 0 1 1H10v-5.5h4V21h3.5a1 1 0 0 0 1-1V9.5" />
    </svg>
  );
}

function ListIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "var(--au-ink-2)"} strokeWidth={active ? 2.2 : 2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 4h8a1 1 0 0 1 1 1v0a2 2 0 0 1-2 2H9A2 2 0 0 1 7 5v0a1 1 0 0 1 1-1ZM7 6H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-1M9 12h6M9 16h4" />
    </svg>
  );
}

function ChartIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "var(--au-ink-2)"} strokeWidth={active ? 2.2 : 2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 21V11M12 21V4M19 21v-7" />
    </svg>
  );
}

function SettingsIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "var(--au-ink-2)"} strokeWidth={active ? 2.2 : 2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z M19.4 13a7.6 7.6 0 0 0 .1-1 7.6 7.6 0 0 0-.1-1l1.7-1.3-2-3.4-2 .8a7.3 7.3 0 0 0-1.7-1l-.3-2.1H9.9l-.3 2.1a7.3 7.3 0 0 0-1.7 1l-2-.8-2 3.4L5.6 11a7.6 7.6 0 0 0 0 2l-1.7 1.3 2 3.4 2-.8a7.3 7.3 0 0 0 1.7 1l.3 2.1h4.2l.3-2.1a7.3 7.3 0 0 0 1.7-1l2 .8 2-3.4Z" />
    </svg>
  );
}
