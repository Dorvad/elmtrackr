package com.elmtrackr.app.language

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * In-app language selection, backed by AndroidX per-app locales.
 *
 * On Android 13+ the choice is stored by the system (and also appears under
 * system Settings → App languages); on older versions appcompat persists it
 * via AppLocalesMetadataHolderService declared in the manifest. Setting a
 * language recreates started activities, so the UI switches immediately.
 *
 * The choice is additionally mirrored into [AppLocaleStore] so background
 * surfaces (widgets, notifications, shortcuts) can resolve strings in the
 * chosen language on Android 12 and below via [withAppLocale].
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    HEBREW("he"),
    ARABIC("ar"),
    ;

    companion object {
        // Java Locale reports Hebrew with the legacy ISO code "iw".
        private val HEBREW_CODES = setOf("he", "iw")

        /**
         * The language an ISO code renders as — never [SYSTEM], since a code
         * has already been resolved to a real language. Matched on the tags
         * rather than name by name, so a new entry above is all it takes.
         * Codes the app has no UI for resolve to English, mirroring how
         * resources fall back to res/values.
         *
         * Use this for the language the UI is *currently drawn in* (read off
         * the configuration); use [current] for the stored choice.
         */
        fun forLanguageCode(code: String?): AppLanguage {
            if (code == null) return ENGLISH
            if (code in HEBREW_CODES) return HEBREW
            return entries.firstOrNull { it.tag == code } ?: ENGLISH
        }

        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales[0]?.language ?: return SYSTEM
            return forLanguageCode(language)
        }

        fun apply(context: Context, language: AppLanguage) {
            val appContext = context.applicationContext
            AppLocaleStore.save(appContext, language.tag)
            val localeList = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(localeList)
            // Activities recreate themselves, but home-screen widgets hold
            // stored labels (date line, last-punch text) formatted in the
            // previous language until the next state change — rebuild them now.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    com.elmtrackr.app.widget.WidgetActions.refreshWidgets(appContext)
                }
            }
        }
    }
}

/**
 * Synchronously readable copy of the in-app language choice, for contexts
 * where AppCompatDelegate has not applied locales (widgets, notifications,
 * receivers on Android 12 and below).
 */
object AppLocaleStore {
    private const val PREFS_NAME = "app_locale"
    private const val KEY_TAG = "language_tag"

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }.apply()
    }

    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TAG, null)
}

/**
 * Returns a context whose resources resolve in the in-app language. On
 * Android 13+ the system already applies the per-app locale process-wide,
 * so the receiver is returned unchanged.
 */
fun Context.withAppLocale(): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val tag = AppLocaleStore.load(this) ?: return this
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
