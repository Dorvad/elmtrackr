import type { Metadata } from "next";
import { LegalDocument } from "@/components/legal/LegalDocument";

export const metadata: Metadata = {
  title: "Terms of Service — ElmTrackr",
  description: "Terms of use for the ElmTrackr shift tracking app.",
};

export default function TermsPage() {
  return <LegalDocument kind="terms" />;
}
