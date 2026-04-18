"use client";

export const dynamic = "force-dynamic";

import { BottomNav } from "@/components/layout/BottomNav";

export default function ProjectsPage() {
  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      <div className="px-4 pt-12 pb-4 animate-fade-in">
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Projects</h1>
      </div>

      <div className="max-w-md mx-auto px-4">
        <div className="rounded-2xl bg-white border border-gray-100 shadow-sm p-8 flex flex-col items-center text-center animate-fade-in-up">
          <div className="h-16 w-16 rounded-2xl bg-violet-100 flex items-center justify-center mb-4">
            <svg className="h-8 w-8 text-violet-600" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
            </svg>
          </div>
          <h2 className="text-lg font-bold text-gray-900 mb-2">Paid Projects</h2>
          <p className="text-sm text-gray-400 leading-relaxed mb-5">
            Organize shifts by project or client and track earnings per project. This feature is in early access.
          </p>
          <span className="rounded-full bg-violet-100 text-violet-700 text-xs font-bold px-3 py-1 uppercase tracking-wide">
            Coming Soon
          </span>
        </div>
      </div>

      <BottomNav />
    </div>
  );
}
