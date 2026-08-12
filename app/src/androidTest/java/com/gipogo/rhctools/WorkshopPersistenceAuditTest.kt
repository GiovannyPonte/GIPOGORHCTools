package com.gipogo.rhctools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.report.CalcEntry
import com.gipogo.rhctools.report.CalcType
import com.gipogo.rhctools.report.LineItem
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.reporting.builder.LongitudinalReportBuilder
import com.gipogo.rhctools.reporting.model.RowUi
import com.gipogo.rhctools.reporting.model.StudyPageUi
import com.gipogo.rhctools.workshop.WorkshopPrefill
import com.gipogo.rhctools.workshop.WorkshopPrefillStore
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import com.gipogo.rhctools.workshop.persistence.WorkshopStudyFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkshopPersistenceAuditTest {

    @Test
    fun completeWorkshop_persistsRealStudy_andPreservesFickAndTdSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = when (val result = DbProvider.getResult(context)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw AssertionError("DB unavailable", result.error)
        }
        val repo = PatientsRepository.get(context)
        val patientDao = db.patientDao()
        val rhcDao = db.rhcStudyDao()

        cleanupAuditPatients(repo, patientDao.list("WKTAUD-").first())

        try {
            val patientId = when (
                val result = repo.createPatient(
                    internalCode = "WKTAUD-001",
                    displayName = "Paciente Taller Audit",
                    sex = "F",
                    birthDateMillis = 631152000000L,
                    notes = "Workshop persistence audit",
                    weightKg = 62.0,
                    heightCm = 168.0
                )
            ) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> throw AssertionError("Patient seed failed: ${result.error}")
            }

            val studyId = WorkshopStudyFactory.startNewRhcStudy(context, patientId)
            WorkshopPrefillStore.set(
                WorkshopPrefill(
                    weightKg = 62.0,
                    heightCm = 168.0,
                    sex = "F",
                    birthDateMillis = 631152000000L
                )
            )

            seedCompleteWorkshopSnapshot(
                coMethod = "FICK",
                co = 4.8,
                ci = 2.9,
                pvrWood = 3.2,
                pvrDyn = 256.0,
                pvrUnits = "WOOD",
                svrWood = 15.0,
                svrDyn = 1200.0,
                map = 82.0,
                rap = 8.0,
                mpap = 28.0,
                pawp = 13.0,
                vo2 = 210.0,
                saO2 = 98.0,
                svO2 = 68.0,
                hb = 13.4,
                hr = 76.0
            )
            WorkshopRhcAutosave.setCoMethod("FICK")
            WorkshopRhcAutosave.setCoSelectionReason("USER_SELECTED")
            WorkshopRhcAutosave.flushNowAndWait(context)

            seedCompleteWorkshopSnapshot(
                coMethod = "TD",
                co = 5.6,
                ci = 3.4,
                pvrWood = 2.7,
                pvrDyn = 216.0,
                pvrUnits = "DYN",
                svrWood = 13.8,
                svrDyn = 1104.0,
                map = 80.0,
                rap = 7.0,
                mpap = 26.0,
                pawp = 12.0,
                vo2 = 210.0,
                saO2 = 98.0,
                svO2 = 71.0,
                hb = 13.4,
                hr = 74.0
            )
            WorkshopRhcAutosave.setCoMethod("TD")
            WorkshopRhcAutosave.setCoSelectionReason("USER_SELECTED")
            WorkshopRhcAutosave.flushNowAndWait(context)

            val persisted = rhcDao.getByStudyId(studyId)
            assertNotNull(persisted)
            assertEquals(62.0, persisted?.weightKg)
            assertEquals(168.0, persisted?.heightCm)
            assertEquals(4.8, persisted?.cardiacOutputFickLMin)
            assertEquals(4.8 / 1.65, persisted?.cardiacIndexFickLMinM2 ?: Double.NaN, 1e-9)
            assertEquals(5.6, persisted?.cardiacOutputTdLMin)
            assertEquals(5.6 / 1.65, persisted?.cardiacIndexTdLMinM2 ?: Double.NaN, 1e-9)
            assertEquals(5.6, persisted?.cardiacOutputSelectedLMin)
            assertEquals(5.6 / 1.65, persisted?.cardiacIndexSelectedLMinM2 ?: Double.NaN, 1e-9)
            assertEquals("TD", persisted?.coSelectedMethod)
            assertEquals("USER_SELECTED", persisted?.coSelectionReason)
            assertEquals("DYN", persisted?.pvrUnits)
            // Derived values must be rebuilt from canonical pressures and the
            // selected TD CO (5.6), never copied from the older Fick snapshot.
            val expectedPvrWu = (26.0 - 12.0) / 5.6
            val expectedSvrWu = (80.0 - 7.0) / 5.6
            assertEquals(expectedPvrWu, persisted?.pvrWood ?: Double.NaN, 1e-9)
            assertEquals(expectedPvrWu * 80.0, persisted?.pvrDyn ?: Double.NaN, 1e-9)
            assertEquals(expectedSvrWu, persisted?.svrWood ?: Double.NaN, 1e-9)
            assertEquals(expectedSvrWu * 80.0, persisted?.svrDyn ?: Double.NaN, 1e-9)

            val studies = rhcDao.listStudiesWithRhcDataByPatient(patientId).first()
            assertEquals(1, studies.size)
            assertEquals(studyId, studies.first().study.id)
            assertEquals("TD", studies.first().rhc?.coSelectedMethod)

            val report = LongitudinalReportBuilder.buildFromRoom(
                context = context,
                patientId = patientId,
                patientDao = db.patientDao(),
                rhcStudyDao = rhcDao
            )
            val studyPages = report.pages.filterIsInstance<StudyPageUi>()
            assertEquals(1, studyPages.size)
            assertTrue(
                studyPages.first().study.sections
                    .flatMap { it.rows }
                    .containsMethodRow(context.getString(R.string.co_method_thermodilution))
            )
        } finally {
            ReportStore.clear()
            WorkshopPrefillStore.clear()
            WorkshopRhcAutosave.clearCoMethod()
            WorkshopRhcAutosave.clearCoSelectionReason()
            WorkshopSession.clear()
            cleanupAuditPatients(repo, patientDao.list("WKTAUD-").first())
        }
    }

    private fun seedCompleteWorkshopSnapshot(
        coMethod: String,
        co: Double,
        ci: Double,
        pvrWood: Double,
        pvrDyn: Double,
        pvrUnits: String,
        svrWood: Double,
        svrDyn: Double,
        map: Double,
        rap: Double,
        mpap: Double,
        pawp: Double,
        vo2: Double,
        saO2: Double,
        svO2: Double,
        hb: Double,
        hr: Double
    ) {
        val base = System.currentTimeMillis()

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.FICK,
                timestampMillis = base,
                title = "Fick/TD audit",
                inputs = listOf(
                    item(SharedKeys.WEIGHT_KG, "Weight", "62.0", "kg"),
                    item(SharedKeys.HEIGHT_CM, "Height", "168.0", "cm"),
                    item(SharedKeys.SAO2_PERCENT, "SaO2", saO2.toString(), "%"),
                    item(SharedKeys.SVO2_PERCENT, "SvO2", svO2.toString(), "%"),
                    item(SharedKeys.HB_GDL, "Hb", hb.toString(), "g/dL"),
                    item(SharedKeys.HR_BPM, "HR", hr.toString(), "bpm"),
                    item(SharedKeys.VO2_MLMIN, "VO2", vo2.toString(), "mL/min"),
                    item(SharedKeys.VO2_MODE, "VO2 mode", "MEASURED"),
                    item(SharedKeys.CO_METHOD, "Method", coMethod)
                ),
                outputs = listOf(
                    item(SharedKeys.CO_LMIN, "CO", co.toString(), "L/min"),
                    item(SharedKeys.BSA_M2, "BSA", "1.65", "m2"),
                    item(SharedKeys.CI_LMIN_M2, "CI", ci.toString(), "L/min/m2")
                )
            )
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.SVR,
                timestampMillis = base + 1,
                title = "SVR audit",
                inputs = listOf(
                    item(SharedKeys.MAP_MMHG, "MAP", map.toString(), "mmHg"),
                    item(SharedKeys.RAP_MMHG, "RAP", rap.toString(), "mmHg")
                ),
                outputs = listOf(
                    item(SharedKeys.SVR_WOOD, "SVR", svrWood.toString(), "WU"),
                    item(SharedKeys.SVR_DYN, "SVR", svrDyn.toString(), "dyn"),
                    item(SharedKeys.SVR_UNITS, "SVR units", "WOOD")
                )
            )
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PVR,
                timestampMillis = base + 2,
                title = "PVR audit",
                inputs = listOf(
                    item(SharedKeys.MPAP_MMHG, "mPAP", mpap.toString(), "mmHg"),
                    item(SharedKeys.PAWP_MMHG, "PAWP", pawp.toString(), "mmHg")
                ),
                outputs = listOf(
                    item(SharedKeys.PVR_WOOD, "PVR", pvrWood.toString(), "WU"),
                    item(SharedKeys.PVR_DYN, "PVR", pvrDyn.toString(), "dyn"),
                    item(SharedKeys.PVR_UNITS, "PVR units", pvrUnits)
                )
            )
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.CPO,
                timestampMillis = base + 3,
                title = "CPO audit",
                inputs = emptyList(),
                outputs = listOf(
                    item(SharedKeys.CPO_W, "CPO", "0.92", "W"),
                    item(SharedKeys.CPI_W_M2, "CPI", "0.56", "W/m2")
                )
            )
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PAPI,
                timestampMillis = base + 4,
                title = "PAPI audit",
                inputs = emptyList(),
                outputs = listOf(
                    item(SharedKeys.PAPI, "PAPI", "1.70")
                )
            )
        )
    }

    private fun item(key: String, label: String, value: String, unit: String? = null): LineItem {
        return LineItem(key = key, label = label, value = value, unit = unit)
    }

    private suspend fun cleanupAuditPatients(
        repo: PatientsRepository,
        leftovers: List<PatientEntity>
    ) {
        leftovers.forEach { repo.deletePatient(it.id) }
    }

    private fun List<RowUi>.containsMethodRow(expectedSnippet: String): Boolean {
        return any { row ->
            row.labelRes == R.string.co_method_label && row.valueText.contains(expectedSnippet, ignoreCase = true)
        }
    }
}
