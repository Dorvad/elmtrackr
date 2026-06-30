package com.elmtrackr.app.ui.settings

data class LegalSection(val title: String, val body: String)

object LegalDocuments {
    const val CONTACT_EMAIL = "support@elmtrackr.app"
    const val LAST_UPDATED = "June 26, 2025"
    const val PRIVACY_POLICY_URL = "https://www.dorsfolio.online/elmtrackr-privacy.html"

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
