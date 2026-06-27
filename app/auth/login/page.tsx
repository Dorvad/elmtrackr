"use client";

import Link from "next/link";
import { useState, FormEvent, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { createClient, isMissingConfig } from "@/lib/supabase/client";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function LoginPage() {
  return (
    <Suspense>
      <LoginPageContent />
    </Suspense>
  );
}

function LoginPageContent() {
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"sign_in" | "sign_up">("sign_in");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const missingConfig = isMissingConfig();
  const callbackError = searchParams.get("error");
  const callbackErrorMessage =
    callbackError === "auth_callback_failed"
      ? "That sign-in link is invalid or has expired. Try signing in again or request a new confirmation email."
      : callbackError
        ? "Authentication failed. Please try again."
        : null;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (missingConfig) return;
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      const supabase = createClient();
      if (mode === "sign_in") {
        const { error: err } = await supabase.auth.signInWithPassword({ email, password });
        if (err) throw err;
        window.location.href = "/";
      } else {
        const { error: err } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: `${window.location.origin}/auth/callback` },
        });
        if (err) throw err;
        setSuccess("Check your email for a confirmation link, then come back to sign in.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Authentication failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex flex-col" style={{ background: "var(--color-surface)" }}>
      {/* Top gradient hero */}
      <div className="relative overflow-hidden bg-gradient-to-br from-indigo-950 via-indigo-900 to-violet-900 px-6 pt-20 pb-16 flex flex-col items-center">
        {/* Ambient blobs */}
        <div className="absolute -top-12 -left-12 w-56 h-56 rounded-full bg-indigo-600/30 blur-3xl pointer-events-none" />
        <div className="absolute top-8 right-0 w-40 h-40 rounded-full bg-violet-500/20 blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-1/4 w-32 h-32 rounded-full bg-blue-500/10 blur-2xl pointer-events-none" />

        {/* Logo mark */}
        <div className="relative mb-4 h-16 w-16 rounded-2xl bg-white/10 border border-white/20 flex items-center justify-center shadow-xl animate-scale-in">
          <svg className="h-8 w-8 text-white" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>

        <h1 className="relative text-3xl font-extrabold text-white tracking-tight animate-fade-in-up stagger-1">
          ElmTrackr
        </h1>
        <p className="relative text-indigo-300 text-sm font-medium mt-1.5 animate-fade-in-up stagger-2">
          Personal shift &amp; hours tracker
        </p>

        {/* Floating mini stat cards */}
        <div className="relative flex gap-3 mt-8 animate-fade-in-up stagger-3">
          {[
            { label: "Hours/mo", value: "160h" },
            { label: "Overtime", value: "12h" },
            { label: "Shifts", value: "22" },
          ].map((s) => (
            <div
              key={s.label}
              className="rounded-2xl bg-white/10 border border-white/15 px-4 py-2.5 text-center backdrop-blur-sm"
            >
              <p className="text-white font-extrabold text-lg leading-none">{s.value}</p>
              <p className="text-indigo-300 text-[10px] font-semibold mt-0.5 uppercase tracking-wide">{s.label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Auth card — slightly overlaps hero */}
      <div className="flex-1 flex flex-col items-center px-4 -mt-5">
        <div className="w-full max-w-sm bg-white rounded-3xl border border-gray-100 shadow-xl shadow-gray-200/60 p-6 animate-fade-in-up stagger-4">
          {/* Mode toggle */}
          <div className="flex rounded-xl bg-gray-100 p-1 mb-5">
            {(["sign_in", "sign_up"] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => { setMode(m); setError(null); setSuccess(null); }}
                className={[
                  "flex-1 rounded-[10px] py-2 text-sm font-bold transition-all duration-200",
                  mode === m
                    ? "bg-white text-indigo-700 shadow-sm"
                    : "text-gray-400 hover:text-gray-600",
                ].join(" ")}
              >
                {m === "sign_in" ? "Sign In" : "Create Account"}
              </button>
            ))}
          </div>

          {/* Setup required banner */}
          {missingConfig && (
            <div className="rounded-2xl bg-amber-50 border border-amber-200 px-4 py-3 mb-2">
              <p className="text-sm font-bold text-amber-800">Supabase not configured</p>
              <p className="text-xs text-amber-700 mt-1 leading-relaxed">
                Copy <code className="bg-amber-100 px-1 rounded font-mono">.env.local.example</code> to{" "}
                <code className="bg-amber-100 px-1 rounded font-mono">.env.local</code> and add your
                Supabase project URL and anon key, then restart the dev server.
              </p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <Input
              label="Password"
              type="password"
              autoComplete={mode === "sign_in" ? "current-password" : "new-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
            />

            {mode === "sign_in" && (
              <div className="text-right -mt-2">
                <Link
                  href="/auth/forgot-password"
                  className="text-sm font-semibold"
                  style={{ color: "var(--au-indigo)" }}
                >
                  Forgot password?
                </Link>
              </div>
            )}

            {callbackErrorMessage && !error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                {callbackErrorMessage}
              </div>
            )}

            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                {error}
              </div>
            )}
            {success && (
              <div className="rounded-2xl bg-emerald-50 border border-emerald-100 px-4 py-3 text-sm text-emerald-700 font-medium">
                {success}
              </div>
            )}

            <Button type="submit" loading={loading} disabled={missingConfig} fullWidth size="lg" className="mt-1">
              {mode === "sign_in" ? "Sign In" : "Create Account"}
            </Button>

            {mode === "sign_up" && (
              <p className="text-xs text-center text-gray-400 leading-relaxed -mt-1">
                By creating an account you agree to our{" "}
                <Link href="/terms" className="font-semibold" style={{ color: "var(--au-indigo)" }}>
                  Terms of Service
                </Link>{" "}
                and{" "}
                <Link href="/privacy" className="font-semibold" style={{ color: "var(--au-indigo)" }}>
                  Privacy Policy
                </Link>
                .
              </p>
            )}
          </form>
        </div>
      </div>
    </div>
  );
}
