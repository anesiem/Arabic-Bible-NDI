package com.arabicchristianmedia.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 1.9.25 (item 2, language mode): the interface-language choice.
 *
 * Choices: [SYSTEM] (follow the device language — the default), [ARABIC],
 * [ENGLISH]. Bible text is unaffected (interface language only).
 *
 * Two mechanisms, by API level:
 * - API 33+: [LocaleManager.applicationLocales] is the source of truth —
 *   it is what Android's per-app language picker in system Settings reads
 *   and writes, and it applies process-wide (Application + Activities).
 *   The SharedPreferences value is kept in sync as a mirror.
 * - API < 33 (no LocaleManager): the SharedPreferences value is the source
 *   of truth, applied by wrapping the base context in attachBaseContext
 *   (both the Application — so the ViewModel's getString resolves in the
 *   app language — and MainActivity).
 *
 * A locale change recreates the Activity (locale is deliberately NOT in
 * MainActivity's android:configChanges), so every getString re-resolves
 * and RTL/LTR layout direction follows the new language.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    private const val PREFS = "ui_prefs"
    private const val KEY = "app_language"

    /** The current choice: LocaleManager wins on API 33+, else the pref. */
    fun currentChoice(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return when (locales[0].language) {
                    ARABIC -> ARABIC
                    ENGLISH -> ENGLISH
                    else -> SYSTEM
                }
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM
    }

    /**
     * Persist a new choice (pref + LocaleManager on API 33+). The caller
     * recreates the Activity afterwards so the new language takes effect.
     */
    fun applyChoice(context: Context, choice: String) {
        val safe = when (choice) {
            ARABIC, ENGLISH -> choice
            else -> SYSTEM
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, safe).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager?.applicationLocales = if (safe == SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(safe)
            }
        }
    }

    /**
     * Wrap [base] with the effective locale for attachBaseContext. Returns
     * [base] unchanged for the system choice (or when the locale already
     * matches). On API 33+ this agrees with applicationLocales; on older
     * APIs it is the mechanism that makes the choice stick.
     */
    fun wrap(base: Context): Context {
        val choice = currentChoice(base)
        if (choice == SYSTEM) return base
        val locale = Locale.forLanguageTag(choice)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** True when the UI is currently rendering in Arabic. */
    fun isArabicUi(context: Context): Boolean {
        val locales = context.resources.configuration.locales
        return !locales.isEmpty && locales[0].language == ARABIC
    }

    /**
     * The effective interface language as a concrete "ar"/"en" — the
     * choice, with SYSTEM resolved to the device language. Used by the
     * HTTP server as the web pages' default language.
     */
    fun effectiveLang(context: Context): String {
        return when (currentChoice(context)) {
            ARABIC -> ARABIC
            ENGLISH -> ENGLISH
            else -> if (Locale.getDefault().language == ARABIC) ARABIC else ENGLISH
        }
    }
}
