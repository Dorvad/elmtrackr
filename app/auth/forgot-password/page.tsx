"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { createClient, isMissingConfig } from "@/lib/supabase/client";
import { getPasswordResetRedirectUrl } from "@/lib/supabase/auth-redirects";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const missingConfig = isMissingConfig();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (missingConfig) return;
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      const supabase = createClient();
      const { error: err } = await supabase.auth.resetPasswordForEmail(email.trim(), {
        redirectTo: getPasswordResetRedirectUrl(),
      });
      if (err) throw err;
      setSuccess(`If an account exists for ${email.trim()}, we sent a password reset link. Check your inbox and spam folder.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to send reset email.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4" style={{ background: "var(--au-bg)" }}>
      <div className="w-full max-w-sm bg-white rounded-3xl border border-gray-100 shadow-xl shadow-gray-200/60 p-6">
        <h1 className="text-2xl font-bold mb-2" style={{ color: "var(--au-ink)" }}>
          Reset your password
        </h1>
        <p className="text-sm mb-6" style={{ color: "var(--au-ink-2)" }}>
          Enter your email and we&apos;ll send you a link to choose a new password.
        </p>

        {missingConfig && (
          <div className="rounded-2xl bg-amber-50 border border-amber-200 px-4 py-3 mb-4 text-sm text-amber-800">
            Supabase is not configured for this environment.
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

          <Button type="submit" loading={loading} disabled={missingConfig || !!success} fullWidth size="lg">
            Send reset link
          </Button>
        </form>

        <p className="text-sm text-center mt-6" style={{ color: "var(--au-ink-2)" }}>
          <Link href="/auth/login" className="font-semibold" style={{ color: "var(--au-indigo)" }}>
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
