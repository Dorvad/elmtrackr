"use client";

import Link from "next/link";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { createClient, isMissingConfig } from "@/lib/supabase/client";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center" style={{ background: "var(--au-bg)" }}>
          <p style={{ color: "var(--au-ink-2)" }}>Preparing password reset…</p>
        </div>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const [hasSession, setHasSession] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const missingConfig = isMissingConfig();

  useEffect(() => {
    if (missingConfig) {
      setCheckingSession(false);
      return;
    }

    let cancelled = false;

    async function prepareSession() {
      const supabase = createClient();
      const code = searchParams.get("code");

      if (code) {
        const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
        if (exchangeError) {
          if (!cancelled) {
            setError("This reset link is invalid or has expired. Request a new one.");
            setHasSession(false);
            setCheckingSession(false);
          }
          return;
        }
      }

      const { data } = await supabase.auth.getUser();
      if (!cancelled) {
        setHasSession(!!data.user);
        if (!data.user) {
          setError("Your reset session has expired. Request a new password reset link.");
        }
        setCheckingSession(false);
      }
    }

    prepareSession();
    return () => {
      cancelled = true;
    };
  }, [missingConfig, searchParams]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 6) {
      setError("Password must be at least 6 characters.");
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setError(null);
    setLoading(true);

    try {
      const supabase = createClient();
      const { error: err } = await supabase.auth.updateUser({ password });
      if (err) throw err;
      router.replace("/");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update password.");
    } finally {
      setLoading(false);
    }
  }

  if (checkingSession) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: "var(--au-bg)" }}>
        <p style={{ color: "var(--au-ink-2)" }}>Preparing password reset…</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4" style={{ background: "var(--au-bg)" }}>
      <div className="w-full max-w-sm bg-white rounded-3xl border border-gray-100 shadow-xl shadow-gray-200/60 p-6">
        <h1 className="text-2xl font-bold mb-2" style={{ color: "var(--au-ink)" }}>
          Choose a new password
        </h1>
        <p className="text-sm mb-6" style={{ color: "var(--au-ink-2)" }}>
          Enter a new password for your ElmTrackr account.
        </p>

        {!hasSession ? (
          <div className="space-y-4">
            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                {error}
              </div>
            )}
            <Link
              href="/auth/forgot-password"
              className="block text-center font-semibold"
              style={{ color: "var(--au-indigo)" }}
            >
              Request a new reset link
            </Link>
            <Link
              href="/auth/login"
              className="block text-center text-sm"
              style={{ color: "var(--au-ink-2)" }}
            >
              Back to sign in
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              label="New password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
            />
            <Input
              label="Confirm password"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={6}
            />

            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-100 px-4 py-3 text-sm text-red-700 font-medium">
                {error}
              </div>
            )}

            <Button type="submit" loading={loading} fullWidth size="lg">
              Update password
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
