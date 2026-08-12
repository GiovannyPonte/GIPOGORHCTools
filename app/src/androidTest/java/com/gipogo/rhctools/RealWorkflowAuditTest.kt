package com.gipogo.rhctools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.core.result.DataError
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.dao.PatientDao
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.report.PatientPdfExport
import com.gipogo.rhctools.reporting.builder.LongitudinalReportBuilder
import com.gipogo.rhctools.reporting.model.RowUi
import com.gipogo.rhctools.reporting.model.StudyPageUi
import com.gipogo.rhctools.reporting.model.TrendsPageUi
import com.gipogo.rhctools.ui.validation.NumericRule
import com.gipogo.rhctools.ui.validation.NumericValidators
import com.gipogo.rhctools.ui.validation.Severity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealWorkflowAuditTest {

    @Test
    fun seedFivePatients_andAuditCriticalFlows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = when (val result = DbProvider.getResult(context)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw AssertionError("DB unavailable", result.error)
        }
        val repo = PatientsRepository.get(context)
        val patientDao = db.patientDao()
        val studyDao = db.studyDao()
        val rhcDao = db.rhcStudyDao()

        cleanupAuditPatients(repo, patientDao)

        try {
            val created = createAuditPatients(repo)
            assertEquals(5, created.size)

            val p1 = created.getValue("ITAUD-001")
            val p2 = created.getValue("ITAUD-002")
            val p3 = created.getValue("ITAUD-003")
            val p4 = created.getValue("ITAUD-004")

            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p1,
                studyOffsetDays = 5,
                coMethod = "FICK",
                co = 4.8,
                ci = 2.2,
                pvrWood = 3.2,
                pvrDyn = 256.0,
                pvrUnits = "WOOD"
            )
            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p1,
                studyOffsetDays = 1,
                coMethod = "FICK",
                co = 5.1,
                ci = 2.4,
                pvrWood = 2.8,
                pvrDyn = 224.0,
                pvrUnits = "WOOD"
            )
            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p2,
                studyOffsetDays = 2,
                coMethod = "TD",
                co = 6.0,
                ci = 3.0,
                pvrWood = 3.4,
                pvrDyn = 272.0,
                pvrUnits = "DYN"
            )
            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p3,
                studyOffsetDays = 4,
                coMethod = "FICK",
                co = 4.4,
                ci = 2.1,
                pvrWood = 4.1,
                pvrDyn = 328.0,
                pvrUnits = "WOOD"
            )
            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p3,
                studyOffsetDays = 0,
                coMethod = "TD",
                co = 5.7,
                ci = 2.9,
                pvrWood = 3.0,
                pvrDyn = 240.0,
                pvrUnits = "DYN"
            )
            seedStudy(
                studyDao = studyDao,
                rhcDao = rhcDao,
                patientId = p4,
                studyOffsetDays = 3,
                coMethod = "FICK",
                co = 4.9,
                ci = 2.3,
                pvrWood = 2.9,
                pvrDyn = 232.0,
                pvrUnits = "WOOD"
            )

            val listed = repo.observePatientsFiltered("ITAUD-", emptySet(), null).first()
            assertEquals(5, listed.size)
            assertEquals("ITAUD-003", listed.first().patient.internalCode)

            val latestP1 = rhcDao.listStudiesWithRhcDataByPatient(p1).first().first()
            assertEquals("FICK", latestP1.rhc?.coSelectedMethod)
            assertEquals(5.1, latestP1.rhc?.cardiacOutputSelectedLMin)
            assertEquals("WOOD", latestP1.rhc?.pvrUnits)

            val latestPdf = PatientPdfExport.exportLatestStudyPdf(
                context = context,
                patientId = p1,
                patientDao = patientDao,
                rhcStudyDao = rhcDao
            )
            assertTrue(latestPdf.pdfFile.exists())
            assertTrue(latestPdf.pdfFile.length() > 0L)

            val homogeneousReport = LongitudinalReportBuilder.buildFromRoom(
                context = context,
                patientId = p1,
                patientDao = patientDao,
                rhcStudyDao = rhcDao
            )
            assertTrue(homogeneousReport.pages.any { page ->
                page is TrendsPageUi && page.titleRes == R.string.patient_trends_section_resistance_title
            })
            val homogeneousStudyPages = homogeneousReport.pages.filterIsInstance<StudyPageUi>()
            assertTrue(homogeneousStudyPages.isNotEmpty())
            assertTrue(
                homogeneousStudyPages.first().study.sections
                    .flatMap { it.rows }
                    .containsMethodRow(context.getString(R.string.co_method_fick))
            )

            val mixedReport = LongitudinalReportBuilder.buildFromRoom(
                context = context,
                patientId = p3,
                patientDao = patientDao,
                rhcStudyDao = rhcDao
            )
            assertTrue(mixedReport.pages.any { page ->
                page is TrendsPageUi && page.titleRes == R.string.patient_trends_section_resistance_title
            })
            val mixedStudyPages = mixedReport.pages.filterIsInstance<StudyPageUi>()
            assertTrue(
                mixedStudyPages.any { page ->
                    page.study.sections.flatMap { it.rows }
                        .containsMethodRow(context.getString(R.string.co_method_thermodilution))
                }
            )

            val invalidNumeric = NumericValidators.validate("abc", NumericRule(hardMin = 1.0, hardMax = 10.0))
            assertEquals(Severity.ERROR, invalidNumeric.severity)

            val hardRange = NumericValidators.validate("999", NumericRule(hardMin = 1.0, hardMax = 10.0))
            assertEquals(Severity.ERROR, hardRange.severity)

            val warningRange = NumericValidators.validate("0.8", NumericRule(hardMin = 0.1, hardMax = 10.0, warnLow = 1.0))
            assertEquals(Severity.WARNING, warningRange.severity)

            val duplicate = repo.createPatient(
                internalCode = "ITAUD-001",
                displayName = "Duplicado",
                sex = "M",
                birthDateMillis = null,
                notes = "ITAUD duplicate"
            )
            assertTrue(duplicate is DataResult.Failure && duplicate.error is DataError.DuplicateCode)

            val createdPatient = patientDao.getById(created.getValue("ITAUD-005"))
            assertNotNull(createdPatient)
            assertEquals("Paciente Audit 5", createdPatient?.displayName)
        } finally {
            cleanupAuditPatients(repo, patientDao)
        }
    }

    private suspend fun createAuditPatients(repo: PatientsRepository): Map<String, String> {
        val codes = listOf("ITAUD-001", "ITAUD-002", "ITAUD-003", "ITAUD-004", "ITAUD-005")
        return codes.associateWith { code ->
            when (val result = repo.createPatient(
                internalCode = code,
                displayName = "Paciente Audit ${code.takeLast(1)}",
                sex = if (code.endsWith("1") || code.endsWith("3")) "M" else "F",
                birthDateMillis = 631152000000L,
                notes = "ITAUD seeded patient $code",
                weightKg = 70.0,
                heightCm = 170.0
            )) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> throw AssertionError("Patient seed failed for $code: ${result.error}")
            }
        }
    }

    private suspend fun seedStudy(
        studyDao: com.gipogo.rhctools.data.db.dao.StudyDao,
        rhcDao: com.gipogo.rhctools.data.db.dao.RhcStudyDao,
        patientId: String,
        studyOffsetDays: Int,
        coMethod: String,
        co: Double,
        ci: Double,
        pvrWood: Double,
        pvrDyn: Double,
        pvrUnits: String
    ) {
        val now = System.currentTimeMillis() - (studyOffsetDays * 24L * 60L * 60L * 1000L)
        val studyId = UUID.randomUUID().toString()
        studyDao.insert(
            StudyEntity(
                id = studyId,
                patientId = patientId,
                type = "RHC",
                startedAtMillis = now,
                endedAtMillis = now + 60_000L,
                notes = "ITAUD study",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        rhcDao.upsertByStudyId(
            RhcStudyDataEntity(
                id = UUID.randomUUID().toString(),
                studyId = studyId,
                rapMmHg = 8.0,
                mpapMmHg = 28.0,
                pawpMmHg = 14.0,
                cardiacOutputLMin = co,
                cardiacIndexLMinM2 = ci,
                cardiacOutputSelectedLMin = co,
                cardiacIndexSelectedLMinM2 = ci,
                coSelectedMethod = coMethod,
                coMethod = coMethod,
                pvrWood = pvrWood,
                pvrDyn = pvrDyn,
                pvrUnits = pvrUnits,
                svrWood = 16.0,
                svrDyn = 1280.0,
                svrUnits = "WOOD",
                cardiacPowerW = 0.9,
                papi = 1.8,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    private suspend fun cleanupAuditPatients(
        repo: PatientsRepository,
        patientDao: PatientDao
    ) {
        val leftovers: List<PatientEntity> = patientDao.list("ITAUD-").first()
        leftovers.forEach { repo.deletePatient(it.id) }
    }

    private fun List<RowUi>.containsMethodRow(expectedSnippet: String): Boolean {
        return any { row ->
            row.labelRes == R.string.co_method_label && row.valueText.contains(expectedSnippet, ignoreCase = true)
        }
    }
}
