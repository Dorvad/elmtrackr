import { redirect } from "next/navigation";
import { TERMS_URL } from "@/lib/legal/content";

export default function TermsPage() {
  redirect(TERMS_URL);
}
