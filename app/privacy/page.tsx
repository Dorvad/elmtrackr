import type { Metadata } from "next";
import { LegalDocument } from "@/components/legal/LegalDocument";

export const metadata: Metadata = {
  title: "Privacy Policy — ElmTrackr",
  description: "How ElmTrackr collects, uses, and protects your data.",
};

export default function PrivacyPage() {
  return <LegalDocument kind="privacy" />;
}
