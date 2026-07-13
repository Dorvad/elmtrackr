package com.elmtrackr.app.ui.settings

import androidx.annotation.StringRes
import com.elmtrackr.app.R

/**
 * A legal document section as string resources, so the text localizes at
 * render time. [bodyArg] is substituted into the body when present.
 */
data class LegalSection(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val bodyArg: String? = null,
)

object LegalDocuments {
    const val CONTACT_EMAIL = "support@elmtrackr.site"
    const val LAST_UPDATED = "June 26, 2025"
    const val PRIVACY_POLICY_URL = "https://elmtrackr.site/privacy"

    val termsOfService: List<LegalSection> = listOf(
        LegalSection(R.string.legal_acceptance_title, R.string.legal_acceptance_body),
        LegalSection(R.string.legal_service_title, R.string.legal_service_body),
        LegalSection(R.string.legal_account_title, R.string.legal_account_body),
        LegalSection(R.string.legal_use_title, R.string.legal_use_body),
        LegalSection(R.string.legal_liability_title, R.string.legal_liability_body),
        LegalSection(R.string.legal_contact_title, R.string.legal_contact_body, CONTACT_EMAIL),
    )
}
