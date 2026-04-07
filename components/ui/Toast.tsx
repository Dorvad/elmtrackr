"use client";

import {
  createContext,
  useCallback,
  useContext,
  useState,
  ReactNode,
} from "react";

type ToastType = "success" | "error" | "info";

interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

interface ToastContextValue {
  toast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const toast = useCallback((message: string, type: ToastType = "info") => {
    const id = Math.random().toString(36).slice(2);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
  }, []);

  const typeConfig: Record<ToastType, { bg: string; icon: string }> = {
    success: { bg: "bg-emerald-600", icon: "✓" },
    error:   { bg: "bg-red-500",     icon: "✕" },
    info:    { bg: "bg-indigo-700",  icon: "·" },
  };

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-2 w-full max-w-sm px-4 pointer-events-none">
        {toasts.map((t) => {
          const cfg = typeConfig[t.type];
          return (
            <div
              key={t.id}
              className={[
                "flex items-center gap-3 rounded-2xl px-4 py-3 shadow-xl pointer-events-auto",
                "toast-enter",
                cfg.bg,
              ].join(" ")}
            >
              <span className="h-5 w-5 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold text-white">
                {cfg.icon}
              </span>
              <span className="text-sm font-semibold text-white">{t.message}</span>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}
