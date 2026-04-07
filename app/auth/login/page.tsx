"use client";

export const dynamic = "force-dynamic";

import { useState, FormEvent } from "react";
import { createClient } from "@/lib/supabase/client";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"sign_in" | "sign_up">("sign_in");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const supabase = createClient();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      if (mode === "sign_in") {
        const { error: err } = await supabase.auth.signInWithPassword({
          email,
          password,
        });
        if (err) throw err;
        // Middleware will redirect to / on next request
        window.location.href = "/";
      } else {
        const { error: err } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: `${window.location.origin}/auth/callback` },
        });
        if (err) throw err;
        setSuccess(
          "Check your email for a confirmation link, then come back to sign in."
        );
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Authentication failed."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        {/* Logo / title */}
        <div className="text-center mb-8">
          <div className="text-4xl mb-2">⏱</div>
          <h1 className="text-2xl font-bold text-gray-900">ElmTrackr</h1>
          <p className="text-sm text-gray-500 mt-1">Personal shift tracker</p>
        </div>

        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          {/* Mode toggle */}
          <div className="flex rounded-xl bg-gray-100 p-0.5 mb-6">
            {(["sign_in", "sign_up"] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => { setMode(m); setError(null); setSuccess(null); }}
                className={[
                  "flex-1 rounded-[10px] py-2 text-sm font-semibold transition-colors",
                  mode === m
                    ? "bg-white text-gray-900 shadow-sm"
                    : "text-gray-500 hover:text-gray-700",
                ].join(" ")}
              >
                {m === "sign_in" ? "Sign In" : "Create Account"}
              </button>
            ))}
          </div>

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

            {error && (
              <p className="rounded-xl bg-red-50 border border-red-100 px-4 py-2.5 text-sm text-red-700">
                {error}
              </p>
            )}
            {success && (
              <p className="rounded-xl bg-green-50 border border-green-100 px-4 py-2.5 text-sm text-green-700">
                {success}
              </p>
            )}

            <Button type="submit" loading={loading} fullWidth size="lg">
              {mode === "sign_in" ? "Sign In" : "Create Account"}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
