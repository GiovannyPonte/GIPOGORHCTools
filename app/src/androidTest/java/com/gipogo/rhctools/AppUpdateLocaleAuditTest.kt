package com.gipogo.rhctools

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AppUpdateLocaleAuditTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun updateMessagesAreCompleteAndLocalizedInSpanishAndEnglish() {
        val ids = listOf(
            R.string.settings_updates_title,
            R.string.settings_updates_description,
            R.string.settings_check_updates,
            R.string.app_update_up_to_date,
            R.string.app_update_not_allowed,
            R.string.app_update_check_failed,
            R.string.app_update_downloaded_title,
            R.string.app_update_downloaded_body,
            R.string.app_update_downloaded_confirm,
            R.string.app_update_downloaded_later,
        )
        val spanish = localizedStrings("es-MX", ids)
        val english = localizedStrings("en-US", ids)

        spanish.forEach(::assertFalseBlank)
        english.forEach(::assertFalseBlank)
        spanish.zip(english).forEach { (es, en) -> assertNotEquals(es, en) }
    }

    private fun assertFalseBlank(value: String) = assertFalse(value.isBlank())

    private fun localizedStrings(languageTag: String, ids: List<Int>): List<String> {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        val localized = context.createConfigurationContext(configuration)
        return ids.map(localized::getString)
    }
}
