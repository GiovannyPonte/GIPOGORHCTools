package com.gipogo.rhctools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.domain.BirthDateCodec
import com.gipogo.rhctools.domain.CanonicalHemodynamics
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.StudyClinicalPdfExport
import com.gipogo.rhctools.report.StudyClinicalPdfFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MexicoSyntheticPatientSeedTest {
    @Test
    fun createCredibleMexicanPatientWithThreeCanonicalStudiesAndReports() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = (DbProvider.getResult(context) as DbProvider.DbOpenResult.Success).db
        val patientDao = db.patientDao()
        val patientRepo = PatientsRepository.get(context)
        val code = "SINT-MX-001"
        patientDao.list(code).first().filter { it.internalCode == code }.forEach { patientRepo.deletePatient(it.id) }

        val patientId = when (val result = patientRepo.createPatient(
            internalCode = code,
            displayName = "María Fernanda López Hernández (FICTICIA)",
            sex = "Femenino",
            birthDateMillis = BirthDateCodec.toStorageMillis(LocalDate.of(1968, 2, 14)),
            notes = "Paciente sintética para demostración. Insuficiencia cardiaca con fracción de eyección preservada, hipertensión arterial y disnea de esfuerzo; sin datos de una persona real.",
            weightKg = 78.0,
            heightCm = 160.0
        )) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> error("No fue posible crear paciente sintética: ${result.error}")
        }

        val studies = listOf(
            Case("Descompensación con congestión", LocalDate.of(2025, 8, 20), 15.0, 58.0, 28.0, 38.0, 26.0, 72.0, 3.6, 53.0,
                "Cateterismo derecho basal: presiones de llenado elevadas, bajo gasto y componente vascular pulmonar combinado."),
            Case("Control temprano tras diuresis", LocalDate.of(2025, 9, 24), 13.0, 54.0, 26.0, 35.0, 23.0, 74.0, 3.9, 56.0,
                "Control temprano: reducción discreta de las presiones de llenado, con persistencia de congestión y bajo flujo relativo."),
            Case("Respuesta parcial al tratamiento", LocalDate.of(2025, 11, 19), 10.0, 49.0, 23.0, 32.0, 20.0, 78.0, 4.3, 60.0,
                "Tras ajuste de diurético y vasodilatación: menor congestión y recuperación parcial del gasto cardiaco."),
            Case("Reevaluación ambulatoria", LocalDate.of(2026, 1, 14), 8.0, 44.0, 20.0, 29.0, 17.0, 80.0, 4.6, 63.0,
                "Reevaluación ambulatoria: presiones cercanas a objetivo, mejoría del índice cardiaco y menor componente poscapilar."),
            Case("Seguimiento clínico estable", LocalDate.of(2026, 2, 18), 7.0, 40.0, 18.0, 27.0, 15.0, 82.0, 4.9, 65.0,
                "Seguimiento: mejoría hemodinámica sostenida, PCWP limítrofe y gasto cardiaco conservado.")
        )
        val bsa = HemodynamicsFormulas.bsaMosteller(160.0, 78.0)
        studies.forEach { item -> seedStudy(db, patientId, bsa, item) }

        val persisted = db.rhcStudyDao().listStudiesWithRhcDataByPatient(patientId).first()
        assertEquals(5, persisted.size)
        persisted.forEach {
            assertNotNull(it.rhc?.cardiacIndexSelectedLMinM2)
            assertNotNull(it.rhc?.pvrWood)
            assertNotNull(it.rhc?.cardiacPowerW)
        }

        val latest = persisted.maxBy { it.study.startedAtMillis }
        val compact = StudyClinicalPdfExport.exportStudyPdf(context, patientId, latest.study.id, StudyClinicalPdfFormat.COMPACT)
        val complete = StudyClinicalPdfExport.exportStudyPdf(context, patientId, latest.study.id, StudyClinicalPdfFormat.COMPLETE)
        val out = File(context.filesDir, "mexico_synthetic_case").apply { mkdirs() }
        compact.pdfFile.copyTo(File(out, "SINT-MX-001_resumen.pdf"), overwrite = true)
        complete.pdfFile.copyTo(File(out, "SINT-MX-001_completo.pdf"), overwrite = true)
        File(out, "patient_id.txt").writeText(patientId)
    }

    private suspend fun seedStudy(db: com.gipogo.rhctools.data.db.AppDatabase, patientId: String, bsa: Double, item: Case) {
        val started = item.date.atTime(9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val studyId = UUID.randomUUID().toString()
        db.studyDao().insert(StudyEntity(studyId, patientId, "RHC", started, started + 45 * 60_000L, item.note, started, started))
        val derived = CanonicalHemodynamics.derive(item.co, bsa, item.map, item.rap, item.pasp, item.padp, item.mpap, item.pcwp)
        db.rhcStudyDao().upsertByStudyId(RhcStudyDataEntity(
            id = UUID.randomUUID().toString(), studyId = studyId,
            weightKg = 78.0, heightCm = 160.0, bsaM2 = bsa,
            saO2Percent = 96.0, svO2Percent = item.svo2, hemoglobinGdl = 12.7, heartRateBpm = 76.0,
            mapMmHg = item.map, rapMmHg = item.rap, paspMmHg = item.pasp, padpMmHg = item.padp,
            mpapMmHg = item.mpap, pawpMmHg = item.pcwp,
            cardiacOutputLMin = item.co, cardiacIndexLMinM2 = derived.cardiacIndexLMinM2,
            cardiacOutputTdLMin = item.co, cardiacIndexTdLMinM2 = derived.cardiacIndexLMinM2,
            cardiacOutputSelectedLMin = item.co, cardiacIndexSelectedLMinM2 = derived.cardiacIndexLMinM2,
            coSelectedMethod = "TD", coSelectionReason = "USER_SELECTED", coMethod = "TD",
            svrWood = derived.svrWood, svrDyn = derived.svrDyn, pvrWood = derived.pvrWood, pvrDyn = derived.pvrDyn,
            papi = derived.papi, cardiacPowerW = derived.cardiacPowerW, cardiacPowerIndexWm2 = derived.cardiacPowerIndexWm2,
            svrUnits = "WOOD", pvrUnits = "WOOD", createdAtMillis = started, updatedAtMillis = started
        ))
    }

    private data class Case(
        val label: String, val date: LocalDate, val rap: Double, val pasp: Double, val padp: Double,
        val mpap: Double, val pcwp: Double, val map: Double, val co: Double, val svo2: Double, val note: String
    )
}
