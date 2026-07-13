import { redirect } from "next/navigation";
import { PRIVACY_POLICY_URL } from "@/lib/legal/content";

export default function PrivacyPage() {
  redirect(PRIVACY_POLICY_URL);
}
