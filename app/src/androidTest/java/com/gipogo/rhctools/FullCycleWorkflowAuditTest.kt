package com.gipogo.rhctools

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.ui.security.AuthSessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FullCycleWorkflowAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() = runBlocking {
        cleanupPatients()
    }

    @Test
    fun patientCrudFlow_createEditDelete_worksThroughRealUi() = runBlocking {
        val code = "CYCAUD-CRUD"

        launchApp()
        openPatientsFromHome()
        openNewPatientForm()

        fillPatientEditor(
            code = code,
            name = "Paciente Ciclo",
            weightKg = "72",
            heightCm = "175"
        )
        savePatient()

        waitForPatientInList(code)
        openPatientMenu(code)
        composeRule.onNodeWithTag("patient_edit_$code").performClick()

        waitForPatientEditorReady()
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_name_field").performTextInput("Paciente Ciclo Editado")
        savePatient()

        waitForPatientInList(code)
        val updated = loadPatientByCode(code)
        assertNotNull(updated)
        assertEquals("Paciente Ciclo Editado", updated?.displayName)

        openPatientMenu(code)
        composeRule.onNodeWithTag("patient_delete_$code").performClick()
        composeRule.onNodeWithTag("patients_delete_confirm_button").performClick()

        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_card_$code").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(loadPatientByCode(code) == null)
    }

    @Test
    fun patientReports_latestExport_opensPdfPreviewWithRealFile() = runBlocking {
        val code = "CYCAUD-REPORTS"
        val patientId = seedPatient(code, "Paciente Reportes")
        seedStudy(patientId, daysAgo = 0)

        launchApp()
        openPatientsFromHome()
        openPatientFromList(code)
        openReportsTab()
        resetPdfSession()
        composeRule.onNodeWithTag("patient_reports_export_latest_button").performScrollTo().assertIsDisplayed().performClick()
        waitForPdfFormatPicker()
        composeRule.onNodeWithTag("study_pdf_format_compact_button").performClick()

        assertPdfExportedWithNameFragment("COMPACT")
    }

    @Test
    fun patientReports_formatPicker_exportsCompactAndCompleteViaRealUi() = runBlocking {
        val code = "CYCAUD-FORMATS"
        val patientId = seedPatient(code, "Paciente Formatos")
        val studyId = seedStudy(
            patientId = patientId,
            daysAgo = 0,
            note = List(18) { "Nota clínica larga de auditoría para forzar una salida PDF multipágina en formato completo $it." }
                .joinToString(" ")
        )

        launchApp()
        openPatientsFromHome()
        openPatientFromList(code)
        openReportsTab()

        resetPdfSession()
        composeRule.onNodeWithTag("patient_reports_export_selected_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_reports_select_study_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("patient_reports_select_study_$studyId").performClick()
        waitForPdfFormatPicker()
        composeRule.onNodeWithTag("study_pdf_format_complete_button").performClick()

        assertPdfExportedWithNameFragment("COMPLETE")
    }

    private fun launchApp() {
        AuthSessionManager.markAuthenticated()
        acceptDisclaimerIfVisible()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("home_open_patients_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPatientsFromHome() {
        composeRule.onNodeWithTag("home_open_patients_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patients_add_fab").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openNewPatientForm() {
        composeRule.onNodeWithTag("patients_add_fab").performClick()
        waitForPatientEditorReady()
    }

    private fun fillPatientEditor(code: String, name: String, weightKg: String, heightCm: String) {
        selectMetricUnits()
        composeRule.onNodeWithTag("patient_code_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_code_field").performTextInput(code)
        composeRule.onNodeWithTag("patient_name_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_name_field").performTextInput(name)
        composeRule.onNodeWithTag("patient_weight_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_weight_field").performTextInput(weightKg)
        composeRule.onNodeWithTag("patient_height_field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("patient_height_field").performTextInput(heightCm)
    }

    private fun savePatient() {
        composeRule.waitUntil(8_000) { isSaveButtonEnabled() }
        composeRule.onNodeWithTag("patient_save_button").performScrollTo().assertIsEnabled().performClick()
    }

    private fun openPatientFromList(code: String) {
        waitForPatientInList(code)
        composeRule.onNodeWithTag("patient_view_$code").performScrollTo().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_detail_new_study_fab").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openReportsTab() {
        composeRule.waitForIdle()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_tab_reports").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("patient_tab_reports").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_reports_export_latest_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPatientMenu(code: String) {
        waitForPatientInList(code)
        composeRule.onNodeWithTag("patient_menu_$code").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("patient_edit_$code").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForPatientInList(code: String) {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_card_$code").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("patient_card_$code").performScrollTo().assertIsDisplayed()
    }

    private fun waitForPatientEditorReady() {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_code_field").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(8_000) {
            runCatching {
                composeRule.onNodeWithTag("patient_code_field")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText]
                true
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

    private fun waitForPdfPreview() {
        composeRule.waitUntil(12_000) {
            composeRule.onAllNodesWithTag("pdf_preview_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("pdf_preview_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf_preview_page_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf_preview_share_button").assertIsDisplayed()
        composeRule.onNodeWithTag("pdf_preview_open_button").assertIsDisplayed()
    }

    private fun waitForPdfFormatPicker() {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("study_pdf_format_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("study_pdf_format_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("study_pdf_format_complete_button").assertIsDisplayed()
        composeRule.onNodeWithTag("study_pdf_format_compact_button").assertIsDisplayed()
    }

    private fun assertPdfExported() {
        composeRule.waitUntil(12_000) {
            PdfSession.lastPdfFile?.exists() == true && (PdfSession.lastPdfFile?.length() ?: 0L) > 0L
        }
        assertTrue(PdfSession.lastPdfFile?.exists() == true)
        assertTrue((PdfSession.lastPdfFile?.length() ?: 0L) > 0L)
    }

    private fun resetPdfSession() {
        PdfSession.lastPdfFile = null
        PdfSession.lastPdfUri = null
    }

    private fun assertPdfExportedWithNameFragment(fragment: String) {
        assertPdfExported()
        val file = PdfSession.lastPdfFile
        assertNotNull(file)
        assertTrue(file!!.name.contains(fragment, ignoreCase = true))
    }

    private suspend fun seedPatient(code: String, name: String): String {
        return when (val result = PatientsRepository.get(composeRule.activity).createPatient(
            internalCode = code,
            displayName = name,
            sex = "M",
            birthDateMillis = null,
            notes = "Full cycle audit",
            weightKg = 72.0,
            heightCm = 175.0
        )) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> error("Could not seed patient $code: ${result.error}")
        }
    }

    private suspend fun seedStudy(
        patientId: String,
        daysAgo: Int,
        note: String = "Full cycle audit study"
    ): String {
        val db = when (val result = DbProvider.getResult(composeRule.activity)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> error("DB unavailable")
        }
        val studyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() - daysAgo * 24L * 60L * 60L * 1000L
        db.studyDao().insert(
            StudyEntity(
                id = studyId,
                patientId = patientId,
                type = "RHC",
                startedAtMillis = now,
                endedAtMillis = now + 60_000L,
                notes = note,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        db.rhcStudyDao().upsertByStudyId(
            RhcStudyDataEntity(
                id = UUID.randomUUID().toString(),
                studyId = studyId,
                rapMmHg = 8.0,
                mpapMmHg = 28.0,
                pawpMmHg = 12.0,
                cardiacOutputLMin = 5.0,
                cardiacIndexLMinM2 = 2.5,
                cardiacOutputSelectedLMin = 5.0,
                cardiacIndexSelectedLMinM2 = 2.5,
                coSelectedMethod = "FICK",
                coMethod = "FICK",
                pvrWood = 3.1,
                pvrDyn = 248.0,
                pvrUnits = "WOOD",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        return studyId
    }

    private suspend fun loadPatientByCode(code: String): PatientEntity? {
        val db = when (val result = DbProvider.getResult(composeRule.activity)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> return null
        }
        return db.patientDao().getByInternalCode(code)
    }

    private suspend fun loadStudiesFor(patientId: String) =
        when (val result = DbProvider.getResult(composeRule.activity)) {
            is DbProvider.DbOpenResult.Success ->
                result.db.rhcStudyDao().listStudiesWithRhcDataByPatient(patientId).first()
            is DbProvider.DbOpenResult.Failure -> emptyList()
        }

    private suspend fun cleanupPatients() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = when (val result = DbProvider.getResult(context)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> return
        }
        val repo = PatientsRepository.get(context)
        val leftovers: List<PatientEntity> = db.patientDao().list("CYCAUD-").first()
        leftovers.forEach { repo.deletePatient(it.id) }
    }

    private fun acceptDisclaimerIfVisible() {
        composeRule.waitForIdle()
        val disclaimerVisible = composeRule.onAllNodesWithTag("disclaimer_accept_button")
            .fetchSemanticsNodes().isNotEmpty()
        if (!disclaimerVisible) return

        composeRule.onNodeWithTag("disclaimer_checkbox").performClick()
        composeRule.onNodeWithTag("disclaimer_accept_button").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("disclaimer_accept_button").fetchSemanticsNodes().isEmpty()
        }
    }
}
