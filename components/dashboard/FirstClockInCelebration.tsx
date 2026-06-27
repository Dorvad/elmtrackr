"use client";

interface FirstClockInCelebrationProps {
  onDismiss: () => void;
}

export function FirstClockInCelebration({ onDismiss }: FirstClockInCelebrationProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
      <div className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-xl text-center animate-fade-in-up">
        <div
          className="mx-auto mb-4 h-16 w-16 rounded-full flex items-center justify-center"
          style={{ background: "var(--au-surface-sub)" }}
        >
          <svg
            className="h-8 w-8"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            viewBox="0 0 24 24"
            style={{ color: "var(--au-indigo)" }}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
          </svg>
        </div>
        <h3 className="text-xl font-bold mb-2" style={{ color: "var(--au-ink)" }}>
          You&apos;re tracking!
        </h3>
        <p className="text-sm leading-relaxed mb-6" style={{ color: "var(--au-ink-2)" }}>
          Your hours, pay estimate, and overtime are live on the home screen. Keep the shift running — or clock out when you&apos;re done.
        </p>
        <button
          type="button"
          onClick={onDismiss}
          className="w-full rounded-2xl text-white font-bold text-base py-4 transition-all active:scale-[0.98]"
          style={{
            background: "var(--au-grad)",
            boxShadow: "0 14px 30px -12px rgba(91,77,242,0.7)",
          }}
        >
          Got it
        </button>
      </div>
    </div>
  );
}
