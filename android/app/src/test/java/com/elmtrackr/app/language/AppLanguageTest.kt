package com.elmtrackr.app.language

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the ISO code a rendered configuration reports mapping back to the
 * language the picker should mark. The onboarding welcome step reads the
 * configuration rather than the stored choice, so this mapping is what decides
 * which card shows a checkmark on first launch.
 */
class AppLanguageTest {

    @Test
    fun `each offered language resolves from its own tag`() {
        AppLanguage.entries
            .filter { it.tag != null }
            .forEach { language ->
                assertEquals(
                    "${language.name} did not resolve from its own tag",
                    language,
                    AppLanguage.forLanguageCode(language.tag),
                )
            }
    }

    /** Java's Locale still reports Hebrew as "iw", so both codes must land on it. */
    @Test
    fun `the legacy Hebrew code resolves to Hebrew`() {
        assertEquals(AppLanguage.HEBREW, AppLanguage.forLanguageCode("iw"))
        assertEquals(AppLanguage.HEBREW, AppLanguage.forLanguageCode("he"))
    }

    /**
     * A device set to a language the app has no UI for draws English from
     * res/values, so the picker has to agree and mark English — not leave every
     * card blank.
     */
    @Test
    fun `an unsupported language falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forLanguageCode("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forLanguageCode(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forLanguageCode(null))
    }

    /** SYSTEM has no tag; it must never be returned for a resolved code. */
    @Test
    fun `no code resolves to SYSTEM`() {
        val codes = listOf("en", "he", "iw", "ar", "fr", "", null)

        codes.forEach { code ->
            assertEquals(
                "code \"$code\" resolved to SYSTEM",
                false,
                AppLanguage.forLanguageCode(code) == AppLanguage.SYSTEM,
            )
        }
    }
}
