package com.jarvis.assistant.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Russian is the first-run language for this Russian-first build.
 * A language explicitly selected by the user is never overwritten.
 */
object LocaleDefaults {
    fun ensureRussian() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
        }
    }
}
