package com.gipogo.rhctools

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRecoveryStore
import com.gipogo.rhctools.ui.security.AuthSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class WorkshopProcessRecoveryAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var patientId: String? = null
    private var studyId: String? = null

    @After
    fun cleanup() {
        runBlocking {
            WorkshopSession.clear()
            runCatching { WorkshopRecoveryStore.clear(context) }
            val db = (DbProvider.getResult(context) as? DbProvider.DbOpenResult.Success)?.db
            studyId?.let { db?.rhcStudyDao()?.deleteByStudyId(it) }
            studyId?.let { db?.studyDao()?.deleteById(it) }
            patientId?.let { db?.patientDao()?.deleteById(it) }
        }
    }

    @Test
    fun interruptedPatientWorkshop_reopensPersistedStudyAndConsumesMarker() = runBlocking {
        acceptDisclaimerIfVisible()
        WorkshopSession.clear()
        WorkshopRecoveryStore.clear(context)

        val db = (DbProvider.getResult(context) as DbProvider.DbOpenResult.Success).db
        val now = System.currentTimeMillis()
        patientId = UUID.randomUUID().toString()
        studyId = UUID.randomUUID().toString()
        db.patientDao().insert(
            PatientEntity(
                id = patientId!!,
                internalCode = "RECOVERY-${now.toString().takeLast(6)}",
                displayName = "Recovery Audit",
                sex = "M",
                birthDateMillis = null,
                notes = null,
                weightKg = 70.0,
                heightCm = 175.0,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        db.studyDao().insert(
            StudyEntity(
                id = studyId!!,
                patientId = patientId!!,
                type = "RHC",
                startedAtMillis = now,
                endedAtMillis = null,
                notes = null,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        WorkshopRecoveryStore.markActive(context, patientId!!, studyId!!, now)

        AuthSessionManager.clear()
        composeRule.activityRule.scenario.recreate()
        val authenticateLabel = context.getString(R.string.auth_locked_patients_cta)
        composeRule.waitUntil(10_000) {
            WorkshopRecoveryStore.read(context) == null &&
                composeRule.onAllNodesWithText(authenticateLabel).fetchSemanticsNodes().isNotEmpty()
        }

        // Tras muerte del proceso la sesión biométrica debe expirar. Simulamos
        // aquí el éxito de autenticación y continuamos desde la barrera segura.
        AuthSessionManager.markAuthenticated()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("study_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("study_detail_screen").assertIsDisplayed()
        assertNull(WorkshopRecoveryStore.read(context))
    }

    private fun acceptDisclaimerIfVisible() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("disclaimer_checkbox").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("disclaimer_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("disclaimer_accept_button").performClick()
        composeRule.waitForIdle()
    }
}
