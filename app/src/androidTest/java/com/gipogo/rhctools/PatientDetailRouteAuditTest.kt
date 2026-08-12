package com.gipogo.rhctools

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.ui.security.AuthSessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PatientDetailRouteAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() = runBlocking {
        cleanupPatients()
    }

    @Test
    fun patientDetail_withoutStudies_showsNewStudyFab() {
        runBlocking {
            val code = "E2EAUD-NOSTUDY"
            seedPatient(code, "Paciente Sin Estudios")

            launchApp()
            openPatientsFromHome()
            openPatientFromList(code)

            composeRule.onNodeWithTag("patient_detail_new_study_fab").assertIsDisplayed()
        }
    }

    @Test
    fun patientDetail_withStudies_listsAllPatientStudies() {
        runBlocking {
            val code = "E2EAUD-WITHSTUDY"
            val patientId = seedPatient(code, "Paciente Con Estudios")
            val studyIds = listOf(
                seedStudy(patientId, daysAgo = 7),
                seedStudy(patientId, daysAgo = 3),
                seedStudy(patientId, daysAgo = 0)
            )

            launchApp()
            openPatientsFromHome()
            openPatientFromList(code)
            composeRule.waitUntil(8_000) {
                composeRule.onAllNodesWithTag("patient_tab_studies", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("patient_tab_studies", useUnmergedTree = true).performClick()

            studyIds.forEach { studyId ->
                composeRule.onNodeWithTag("patient_studies_list")
                    .performScrollToNode(hasTestTag("study_item_$studyId"))
                composeRule.onNodeWithTag("study_item_$studyId").assertIsDisplayed()
            }

            assertEquals(3, loadStudiesFor(patientId).size)
            composeRule.onNodeWithTag("patient_detail_new_study_fab").assertIsDisplayed()
        }
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

    private fun openPatientFromList(code: String) {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_view_$code").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("patient_card_$code").performScrollTo()
        composeRule.onNodeWithTag("patient_view_$code").performScrollTo().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag("patient_detail_new_study_fab").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun seedPatient(code: String, name: String): String {
        return when (val result = PatientsRepository.get(composeRule.activity).createPatient(
            internalCode = code,
            displayName = name,
            sex = "M",
            birthDateMillis = null,
            notes = "Patient detail audit",
            weightKg = 72.0,
            heightCm = 175.0
        )) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> error("Could not seed patient $code: ${result.error}")
        }
    }

    private suspend fun seedStudy(patientId: String, daysAgo: Int): String {
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
                notes = "Audit study",
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
        val leftovers: List<PatientEntity> = db.patientDao().list("E2EAUD-").first()
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
