package com.gipogo.rhctools

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DatabaseErrorLocaleAuditTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun specializedDatabaseErrorsResolveInSpanishAndEnglish() {
        val ids = listOf(
            R.string.db_error_corrupt,
            R.string.db_error_locked,
            R.string.db_error_storage_full,
            R.string.db_error_storage_permission,
            R.string.db_error_storage_io,
            R.string.db_error_downgrade,
            R.string.error_copy_details,
            R.string.db_delete_unreadable_action,
            R.string.db_delete_unreadable_hint,
            R.string.db_delete_unreadable_title,
            R.string.db_delete_unreadable_confirmation,
            R.string.db_delete_unreadable_confirm
        )
        val spanish = localizedStrings("es-MX", ids)
        val english = localizedStrings("en-US", ids)

        spanish.forEach { assertFalse(it.isBlank()) }
        english.forEach { assertFalse(it.isBlank()) }
        assertTrue(spanish.zip(english).all { (es, en) -> es != en })
        assertTrue(spanish.any { it.contains("datos", ignoreCase = true) })
        assertTrue(english.any { it.contains("data", ignoreCase = true) })
    }

    private fun localizedStrings(languageTag: String, ids: List<Int>): List<String> {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        val localized = context.createConfigurationContext(configuration)
        return ids.map(localized::getString)
    }
}
