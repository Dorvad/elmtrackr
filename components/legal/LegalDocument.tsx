import Link from "next/link";
import {
  LEGAL_CONTACT_EMAIL,
  PRIVACY_POLICY_LAST_UPDATED,
  privacyPolicySections,
  termsSections,
} from "@/lib/legal/content";

type LegalKind = "privacy" | "terms";

const CONFIG: Record<
  LegalKind,
  { title: string; lastUpdated: string; sections: readonly { title: string; body: string }[] }
> = {
  privacy: {
    title: "Privacy Policy",
    lastUpdated: PRIVACY_POLICY_LAST_UPDATED,
    sections: privacyPolicySections,
  },
  terms: {
    title: "Terms of Service",
    lastUpdated: PRIVACY_POLICY_LAST_UPDATED,
    sections: termsSections,
  },
};

export function LegalDocument({ kind }: { kind: LegalKind }) {
  const { title, lastUpdated, sections } = CONFIG[kind];

  return (
    <div className="min-h-screen" style={{ background: "var(--au-bg)" }}>
      <div className="max-w-2xl mx-auto px-5 pt-12 pb-16">
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-sm font-semibold mb-6"
          style={{ color: "var(--au-indigo)" }}
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </Link>

        <h1
          className="text-3xl font-bold tracking-tight mb-2"
          style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}
        >
          {title}
        </h1>
        <p className="text-sm mb-8" style={{ color: "var(--au-faint)" }}>
          Last updated: {lastUpdated}
        </p>

        <div className="flex flex-col gap-6">
          {sections.map((section) => (
            <section key={section.title}>
              <h2 className="text-base font-bold mb-2" style={{ color: "var(--au-ink)" }}>
                {section.title}
              </h2>
              <p className="text-sm leading-relaxed" style={{ color: "var(--au-ink-2)" }}>
                {section.body}
              </p>
            </section>
          ))}
        </div>

        <p className="text-sm mt-10 pt-6 border-t" style={{ color: "var(--au-faint)", borderColor: "var(--au-hair)" }}>
          Contact:{" "}
          <a href={`mailto:${LEGAL_CONTACT_EMAIL}`} className="font-semibold" style={{ color: "var(--au-indigo)" }}>
            {LEGAL_CONTACT_EMAIL}
          </a>
        </p>
      </div>
    </div>
  );
}
