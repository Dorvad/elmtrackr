package com.elmtrackr.app.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language selection, backed by AndroidX per-app locales.
 *
 * On Android 13+ the choice is stored by the system (and also appears under
 * system Settings → App languages); on older versions appcompat persists it
 * via AppLocalesMetadataHolderService declared in the manifest. Setting a
 * language recreates started activities, so the UI switches immediately.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    HEBREW("he"),
    ;

    companion object {
        // Java Locale reports Hebrew with the legacy ISO code "iw".
        private val HEBREW_CODES = setOf("he", "iw")

        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales[0]?.language ?: return SYSTEM
            return if (language in HEBREW_CODES) HEBREW else ENGLISH
        }

        fun apply(language: AppLanguage) {
            val localeList = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }
}
