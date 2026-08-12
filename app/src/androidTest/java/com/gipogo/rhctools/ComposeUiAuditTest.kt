package com.gipogo.rhctools

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.ui.screens.DisclaimerScreen
import com.gipogo.rhctools.ui.screens.PatientEditScreen
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ComposeUiAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun cleanup() = runBlocking {
        cleanupUiAuditPatients()
    }

    @Test
    fun disclaimer_requiresCheckboxBeforeAccept() {
        var accepted = false

        composeRule.setContent {
            GipogoRhctoolsTheme {
                DisclaimerScreen(
                    onAccept = { accepted = true },
                    onDecline = {}
                )
            }
        }

        composeRule.onNodeWithTag("disclaimer_accept_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("disclaimer_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("disclaimer_accept_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(3_000) { accepted }
    }

    @Test
    fun patientEdit_blocksInvalidRangeAndShowsHint() {
        composeRule.setContent {
            GipogoRhctoolsTheme {
                PatientEditScreen(
                    isEdit = false,
                    onBack = {},
                    onSave = {},
                    requireAuth = false
                )
            }
        }

        waitForPatientEditorReady()
        selectMetricUnits()

        composeRule.onNodeWithTag("patient_code_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_code_field").performTextInput("UIAUD-RANGE")
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().performTextInput("Paciente Rango")
        composeRule.onNodeWithTag("patient_weight_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_weight_field").performTextInput("999")
        composeRule.onNodeWithTag("patient_height_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_height_field").performTextInput("170")

        assertTrue(
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.patient_edit_warn_weight_range)
            ).fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("patient_save_button").assertIsNotEnabled()
    }

    @Test
    fun patientEdit_showsDuplicateCodeError() {
        runBlocking {
            val repo = PatientsRepository.get(composeRule.activity)
            when (repo.createPatient(
                internalCode = "UIAUD-DUP",
                displayName = "Paciente Base",
                sex = "M",
                birthDateMillis = null,
                notes = "UI audit seed",
                weightKg = 70.0,
                heightCm = 170.0
            )) {
                is DataResult.Success -> Unit
                is DataResult.Failure -> error("Could not seed duplicate patient")
            }
        }

        composeRule.setContent {
            GipogoRhctoolsTheme {
                PatientEditScreen(
                    isEdit = false,
                    onBack = {},
                    onSave = {},
                    requireAuth = false
                )
            }
        }

        waitForPatientEditorReady()
        selectMetricUnits()

        composeRule.onNodeWithTag("patient_code_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_code_field").performTextInput("UIAUD-DUP")
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().performTextInput("Paciente Duplicado")
        composeRule.onNodeWithTag("patient_weight_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_weight_field").performTextInput("70")
        composeRule.onNodeWithTag("patient_height_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_height_field").performTextInput("170")
        composeRule.waitUntil(8_000) {
            isSaveButtonEnabled()
        }
        composeRule.onNodeWithTag("patient_save_button").performScrollTo().assertIsEnabled().performClick()

        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.patient_edit_code_taken)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.patient_edit_code_taken)
            ).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun patientEdit_savesValidPatientThroughRealRepository() {
        val saved = AtomicBoolean(false)
        val code = "UIAUD-${UUID.randomUUID().toString().take(8).uppercase()}"

        composeRule.setContent {
            GipogoRhctoolsTheme {
                PatientEditScreen(
                    isEdit = false,
                    onBack = {},
                    onSave = { saved.set(true) },
                    requireAuth = false
                )
            }
        }

        waitForPatientEditorReady()
        selectMetricUnits()

        composeRule.onNodeWithTag("patient_code_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_code_field").performTextInput(code)
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().performTextInput("Paciente Guardado")
        composeRule.onNodeWithTag("patient_weight_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_weight_field").performTextInput("72")
        composeRule.onNodeWithTag("patient_height_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_height_field").performTextInput("175")
        composeRule.waitUntil(8_000) {
            isSaveButtonEnabled()
        }
        composeRule.onNodeWithTag("patient_save_button").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitUntil(8_000) { saved.get() }

        runBlocking {
            val db = when (val result = DbProvider.getResult(composeRule.activity)) {
                is DbProvider.DbOpenResult.Success -> result.db
                is DbProvider.DbOpenResult.Failure -> error("DB unavailable")
            }
            val stored = db.patientDao().getByInternalCode(code)
            assertTrue(stored != null && stored.displayName == "Paciente Guardado")
        }
    }

    private fun waitForPatientEditor() {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_code_field").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("patient_code_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().assertIsDisplayed()
    }

    private fun waitForPatientEditorReady() {
        waitForPatientEditor()
        composeRule.waitUntil(8_000) {
            runCatching {
                composeRule.onNodeWithTag("patient_code_field")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText]
                    .text
                    .isNotBlank()
            }.getOrDefault(false)
        }
    }

    private fun selectMetricUnits() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("unit_metric_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("unit_metric_button").performScrollTo().performClick()
    }

    private fun isSaveButtonEnabled(): Boolean =
        runCatching {
            composeRule.onNodeWithTag("patient_save_button").assertIsEnabled()
            true
        }.getOrDefault(false)

    private suspend fun cleanupUiAuditPatients() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = when (val result = DbProvider.getResult(context)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> return
        }
        val repo = PatientsRepository.get(context)
        val leftovers: List<PatientEntity> = db.patientDao().list("UIAUD-").first()
        leftovers.forEach { repo.deletePatient(it.id) }
    }
}
