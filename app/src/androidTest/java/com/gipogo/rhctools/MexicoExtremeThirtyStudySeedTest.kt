package com.gipogo.rhctools

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gipogo.rhctools.core.result.DataResult
import com.gipogo.rhctools.data.db.AppDatabase
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.data.patients.PatientsRepository
import com.gipogo.rhctools.domain.BirthDateCodec
import com.gipogo.rhctools.domain.AppLanguage
import com.gipogo.rhctools.domain.CanonicalHemodynamics
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.StudyClinicalPdfExport
import com.gipogo.rhctools.report.StudyClinicalPdfFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MexicoExtremeThirtyStudySeedTest {

    @Test
    fun persistThirtyPlausibleStudiesAndGenerateBothReports() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requestedLanguage = androidx.test.platform.app.InstrumentationRegistry
            .getArguments().getString("reportLanguage")
        if (requestedLanguage != null) {
            AppPreferences(context).setAppLanguage(AppLanguage.fromStored(requestedLanguage))
        }
        val db = (DbProvider.getResult(context) as DbProvider.DbOpenResult.Success).db
        val repository = PatientsRepository.get(context)
        val code = "SINT-MX-030"
        db.patientDao().list(code).first()
            .filter { it.internalCode == code }
            .forEach { repository.deletePatient(it.id) }

        val patientId = when (val result = repository.createPatient(
            internalCode = code,
            displayName = "Alejandro Ramirez Ortega (FICTICIO)",
            sex = "Masculino",
            birthDateMillis = BirthDateCodec.toStorageMillis(LocalDate.of(1959, 11, 3)),
            notes = "Caso sintetico extremo para validar seguimiento longitudinal. Miocardiopatia isquemica, insuficiencia cardiaca avanzada y ajustes seriados de tratamiento; no corresponde a una persona real.",
            weightKg = 84.0,
            heightCm = 173.0
        )) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> error("No fue posible crear el caso sintetico: ${result.error}")
        }

        val bsa = HemodynamicsFormulas.bsaMosteller(173.0, 84.0)
        val startDate = LocalDate.of(2023, 9, 12)
        repeat(30) { index ->
            val progress = index / 29.0
            val oscillation = sin(index * 0.72)
            val relapse = if (index in 13..17) sin((index - 13) / 4.0 * Math.PI) else 0.0
            val rap = (17.0 - 10.0 * progress + 0.7 * oscillation + 2.4 * relapse).coerceIn(5.0, 20.0)
            val pcwp = (29.0 - 13.0 * progress + 1.0 * oscillation + 3.6 * relapse).coerceIn(10.0, 32.0)
            val mpap = (43.0 - 15.0 * progress + 0.8 * oscillation + 3.0 * relapse).coerceIn(20.0, 48.0)
            val pasp = (64.0 - 18.0 * progress + 1.5 * oscillation + 4.0 * relapse).coerceIn(36.0, 70.0)
            val padp = (32.0 - 12.0 * progress + 0.7 * oscillation + 2.6 * relapse).coerceIn(14.0, 36.0)
            val map = (70.0 + 12.0 * progress + 1.2 * oscillation - 2.0 * relapse).coerceIn(65.0, 90.0)
            val co = (3.15 + 1.85 * progress + 0.12 * oscillation - 0.35 * relapse).coerceIn(2.8, 5.4)
            val svo2 = (51.0 + 15.0 * progress + 0.8 * oscillation - 2.5 * relapse).coerceIn(48.0, 69.0)
            val phase = when {
                index == 0 -> "Evaluacion hemodinamica basal con congestion biventricular y bajo flujo."
                index in 1..12 -> "Seguimiento ${index + 1}: respuesta progresiva a descongestion y optimizacion del tratamiento."
                index in 13..17 -> "Seguimiento ${index + 1}: recaida congestiva transitoria con ajuste terapeutico."
                index == 29 -> "Seguimiento 30: estabilidad hemodinamica, flujo conservado y menor carga de llenado."
                else -> "Seguimiento ${index + 1}: recuperacion sostenida despues de la recaida intermedia."
            }
            seedStudy(
                db = db,
                patientId = patientId,
                bsa = bsa,
                date = startDate.plusMonths(index.toLong()),
                sequence = index + 1,
                rap = rap,
                pasp = pasp,
                padp = padp,
                mpap = mpap,
                pcwp = pcwp,
                map = map,
                co = co,
                svo2 = svo2,
                note = phase
            )
        }

        val persisted = db.rhcStudyDao().listStudiesWithRhcDataByPatient(patientId).first()
            .sortedBy { it.study.startedAtMillis }
        assertEquals(30, persisted.size)
        assertTrue(persisted.all { it.rhc?.cardiacIndexSelectedLMinM2 != null && it.rhc?.pvrWood != null })

        val latest = persisted.last()
        val compact = StudyClinicalPdfExport.exportStudyPdf(context, patientId, latest.study.id, StudyClinicalPdfFormat.COMPACT)
        val complete = StudyClinicalPdfExport.exportStudyPdf(context, patientId, latest.study.id, StudyClinicalPdfFormat.COMPLETE)
        assertEquals(1, pageCount(compact.pdfFile))
        assertTrue(pageCount(complete.pdfFile) in 6..7)

        val output = File(requireNotNull(context.getExternalFilesDir(null)), "mexico_extreme_30_case").apply { mkdirs() }
        compact.pdfFile.copyTo(File(output, "SINT-MX-030_resumen.pdf"), overwrite = true)
        complete.pdfFile.copyTo(File(output, "SINT-MX-030_completo.pdf"), overwrite = true)
        File(output, "patient_id.txt").writeText(patientId)
    }

    private suspend fun seedStudy(
        db: AppDatabase,
        patientId: String,
        bsa: Double,
        date: LocalDate,
        sequence: Int,
        rap: Double,
        pasp: Double,
        padp: Double,
        mpap: Double,
        pcwp: Double,
        map: Double,
        co: Double,
        svo2: Double,
        note: String
    ) {
        val started = date.atTime(9, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
        val studyId = UUID.randomUUID().toString()
        db.studyDao().insert(
            StudyEntity(studyId, patientId, "RHC-$sequence", started, started + 45 * 60_000L, note, started, started)
        )
        val derived = CanonicalHemodynamics.derive(co, bsa, map, rap, pasp, padp, mpap, pcwp)
        db.rhcStudyDao().upsertByStudyId(
            RhcStudyDataEntity(
                id = UUID.randomUUID().toString(), studyId = studyId,
                weightKg = 84.0, heightCm = 173.0, bsaM2 = bsa,
                saO2Percent = 95.0, svO2Percent = svo2, hemoglobinGdl = 13.1, heartRateBpm = 78.0,
                mapMmHg = map, rapMmHg = rap, paspMmHg = pasp, padpMmHg = padp,
                mpapMmHg = mpap, pawpMmHg = pcwp,
                cardiacOutputLMin = co, cardiacIndexLMinM2 = derived.cardiacIndexLMinM2,
                cardiacOutputTdLMin = co, cardiacIndexTdLMinM2 = derived.cardiacIndexLMinM2,
                cardiacOutputSelectedLMin = co, cardiacIndexSelectedLMinM2 = derived.cardiacIndexLMinM2,
                coSelectedMethod = "TD", coSelectionReason = "USER_SELECTED", coMethod = "TD",
                svrWood = derived.svrWood, svrDyn = derived.svrDyn,
                pvrWood = derived.pvrWood, pvrDyn = derived.pvrDyn,
                papi = derived.papi, cardiacPowerW = derived.cardiacPowerW,
                cardiacPowerIndexWm2 = derived.cardiacPowerIndexWm2,
                svrUnits = "WOOD", pvrUnits = "WOOD",
                createdAtMillis = started, updatedAtMillis = started
            )
        )
    }

    private fun pageCount(file: File): Int {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer -> renderer.pageCount }.also { descriptor.close() }
    }
}
