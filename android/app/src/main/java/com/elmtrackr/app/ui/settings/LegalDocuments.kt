package com.elmtrackr.app.ui.settings

data class LegalSection(val title: String, val body: String)

object LegalDocuments {
    const val CONTACT_EMAIL = "support@elmtrackr.app"
    const val LAST_UPDATED = "June 26, 2025"

    val privacyPolicy: List<LegalSection> = listOf(
        LegalSection(
            "Overview",
            "ElmTrackr helps you track work shifts, estimate pay, and manage travel refund claims. " +
                "This policy explains what we collect, why, and your choices.",
        ),
        LegalSection(
            "Data we collect",
            "Account: email and optional display name. Work data: shift times, breaks, notes, overtime settings, " +
                "compensation rules, and pay estimates. Refunds: claim details and receipt photos you upload. " +
                "The app stores data locally and syncs to our cloud database when you are signed in.",
        ),
        LegalSection(
            "How we use data",
            "We use your data only to provide app features. Pay figures are estimates for personal reference — not official payroll.",
        ),
        LegalSection(
            "Where data is stored",
            "Cloud data is hosted on Supabase. Local data remains on your device until you delete your account or uninstall.",
        ),
        LegalSection(
            "Sharing",
            "We do not sell your data or use advertising analytics SDKs. Infrastructure providers (e.g. Supabase) process data to run the service.",
        ),
        LegalSection(
            "Retention & deletion",
            "Delete your account anytime from Settings → Account → Delete account. This removes cloud and local app data for your profile.",
        ),
        LegalSection(
            "Contact",
            "Privacy questions: $CONTACT_EMAIL",
        ),
    )

    val termsOfService: List<LegalSection> = listOf(
        LegalSection(
            "Acceptance",
            "By using ElmTrackr you agree to these Terms. If you do not agree, do not use the app.",
        ),
        LegalSection(
            "Service description",
            "ElmTrackr is a personal shift tracker. Pay and overtime figures are estimates only — not tax, legal, or payroll advice.",
        ),
        LegalSection(
            "Your account",
            "Keep your credentials secure. You may delete your account at any time from Settings.",
        ),
        LegalSection(
            "Acceptable use",
            "Do not misuse the service or upload unlawful content. Only store receipt photos you have the right to keep.",
        ),
        LegalSection(
            "Limitation of liability",
            "The app is provided \"as is\". We are not liable for decisions based on estimates or reports.",
        ),
        LegalSection(
            "Contact",
            "Questions: $CONTACT_EMAIL",
        ),
    )
}
