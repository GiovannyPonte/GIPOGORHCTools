package com.gipogo.rhctools.debug

import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.PatientEntity
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity
import com.gipogo.rhctools.domain.HemodynamicsFormulas
import com.gipogo.rhctools.report.PatientPdfExport
import com.gipogo.rhctools.reporting.export.LongitudinalComposePdfExporter
import com.gipogo.rhctools.ui.security.AuthSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * Herramienta exclusiva de debug para la auditoría clínica manual solicitada.
 * Usa la misma Room cifrada, DAOs, fórmulas y exportadores del código de producción.
 * No se compila en release.
 */
class LiveClinicalAuditActivity : ComponentActivity() {
    private var status by mutableStateOf("Preparado: 10 pacientes × 10 talleres")
    private var detail by mutableStateOf("Los datos son sintéticos y permanecerán en la base del emulador.")
    private var progress by mutableStateOf(0f)
    private var running by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Solo debug: permite revisar los casos sintéticos por las pantallas reales
        // sin registrar biometría ficticia en el AVD de auditoría.
        AuthSessionManager.markAuthenticated()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Auditoría clínica en vivo", style = MaterialTheme.typography.headlineMedium)
                        Text("Base cifrada real · 100 talleres · 20 reportes PDF")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(status, style = MaterialTheme.typography.titleMedium)
                        Text(detail, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            enabled = !running,
                            onClick = { runAudit() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (running) "Ejecutando…" else "Ejecutar auditoría completa") }
                    }
                }
            }
        }
        if (intent.getBooleanExtra("run", false)) runAudit()
    }

    private fun runAudit() {
        if (running) return
        running = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { executeAudit() }
                status = "AUDITORÍA APROBADA"
                detail = result
                progress = 1f
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                status = "AUDITORÍA CON ERROR"
                detail = "${error.javaClass.simpleName}: ${error.message ?: "sin detalle"}"
            } finally {
                running = false
            }
        }
    }

    private suspend fun executeAudit(): String {
        val db = when (val result = DbProvider.getResult(applicationContext)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> throw result.error
        }
        val patientDao = db.patientDao()
        val studyDao = db.studyDao()
        val rhcDao = db.rhcStudyDao()
        val outputDir = File(filesDir, "live_clinical_audit").apply { mkdirs() }
        val csv = StringBuilder(
            "patient_code,case,study,started_at,map,rap,pasp,padp,mpap,pawp,co,ci,pvr_wu,svr_wu,papi,cpo_w\n"
        )
        val auditLines = mutableListOf<String>()
        val cases = clinicalCases()

        cases.forEachIndexed { caseIndex, clinicalCase ->
            status = "Paciente ${caseIndex + 1}/10: ${clinicalCase.label}"
            val patientId = "live-audit-p${(caseIndex + 1).toString().padStart(2, '0')}"
            val now = System.currentTimeMillis()
            val existingPatient = patientDao.getByInternalCode(clinicalCase.code)
            val patient = PatientEntity(
                id = existingPatient?.id ?: patientId,
                internalCode = clinicalCase.code,
                displayName = clinicalCase.displayName,
                sex = clinicalCase.sex,
                birthDateMillis = clinicalCase.birthDateMillis,
                weightKg = clinicalCase.weightKg,
                heightCm = clinicalCase.heightCm,
                notes = "CASO SINTÉTICO DE AUDITORÍA · ${clinicalCase.label}",
                createdAtMillis = existingPatient?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            if (existingPatient == null) patientDao.insert(patient) else patientDao.update(patient)

            val bsa = HemodynamicsFormulas.bsaMosteller(clinicalCase.heightCm, clinicalCase.weightKg)
            val snapshots = mutableListOf<RhcStudyDataEntity>()
            repeat(10) { studyIndex ->
                val fraction = studyIndex / 9.0
                val values = clinicalCase.values(fraction)
                val started = BASE_STUDY_TIME + caseIndex * 86_400_000L + studyIndex * 30L * 86_400_000L
                val studyId = "$patientId-s${(studyIndex + 1).toString().padStart(2, '0')}"
                val existingStudy = studyDao.getById(studyId)
                val study = StudyEntity(
                    id = studyId,
                    patientId = patient.id,
                    type = "RHC",
                    startedAtMillis = started,
                    endedAtMillis = started + 45 * 60_000L,
                    notes = "Taller clínico completo ${studyIndex + 1}/10 · ${clinicalCase.label}",
                    createdAtMillis = existingStudy?.createdAtMillis ?: started,
                    updatedAtMillis = started + 45 * 60_000L
                )
                if (existingStudy == null) studyDao.insert(study) else studyDao.update(study)

                val pvr = HemodynamicsFormulas.pulmonaryVascularResistance(values.mpap, values.pawp, values.co)
                val svr = HemodynamicsFormulas.systemicVascularResistance(values.map, values.rap, values.co)
                val papi = HemodynamicsFormulas.papi(values.pasp, values.padp, values.rap).papi
                val cpo = HemodynamicsFormulas.cardiacPowerOutput(values.map, values.co, bsa)
                val vo2 = HemodynamicsFormulas.estimatedVo2MlMin(bsa)
                val ca = HemodynamicsFormulas.oxygenContentMlPerDl(values.hb, values.sao2, null, false)
                val cv = ca - vo2 / (values.co * 10.0)
                val svo2 = (cv / (1.36 * values.hb) * 100.0).coerceIn(25.0, 90.0)
                val tdCo = values.co * (1.0 + ((studyIndex % 3) - 1) * 0.012)
                val entity = RhcStudyDataEntity(
                    id = "$studyId-rhc",
                    studyId = studyId,
                    weightKg = clinicalCase.weightKg,
                    heightCm = clinicalCase.heightCm,
                    bsaM2 = bsa,
                    saO2Percent = values.sao2,
                    svO2Percent = svo2,
                    hemoglobinGdl = values.hb,
                    heartRateBpm = values.hr,
                    vo2MlMin = vo2,
                    vo2Mode = "ESTIMATED",
                    mapMmHg = values.map,
                    rapMmHg = values.rap,
                    paspMmHg = values.pasp,
                    padpMmHg = values.padp,
                    mpapMmHg = values.mpap,
                    pawpMmHg = values.pawp,
                    cardiacOutputLMin = values.co,
                    cardiacIndexLMinM2 = values.co / bsa,
                    cardiacOutputFickLMin = values.co,
                    cardiacIndexFickLMinM2 = values.co / bsa,
                    cardiacOutputTdLMin = tdCo,
                    cardiacIndexTdLMinM2 = tdCo / bsa,
                    cardiacOutputSelectedLMin = values.co,
                    cardiacIndexSelectedLMinM2 = values.co / bsa,
                    coSelectedMethod = "FICK",
                    coSelectionReason = "USER_SELECTED",
                    svrWood = svr.woodUnits,
                    svrDyn = svr.dynesSecCm5,
                    pvrWood = pvr.woodUnits,
                    pvrDyn = pvr.dynesSecCm5,
                    papi = papi,
                    cardiacPowerW = cpo.cpoWatts,
                    cardiacPowerIndexWm2 = cpo.cpiWattsPerM2,
                    svrUnits = "WOOD",
                    pvrUnits = "WOOD",
                    coMethod = "FICK",
                    createdAtMillis = started,
                    updatedAtMillis = started + 45 * 60_000L
                )
                rhcDao.upsertByStudyId(entity)
                val persisted = checkNotNull(rhcDao.getByStudyId(studyId))
                verifySnapshot(persisted)
                snapshots += persisted
                csv.append(
                    listOf(
                        clinicalCase.code, quote(clinicalCase.label), studyIndex + 1, started,
                        values.map, values.rap, values.pasp, values.padp, values.mpap, values.pawp,
                        values.co, values.co / bsa, pvr.woodUnits, svr.woodUnits, papi, cpo.cpoWatts
                    ).joinToString(",")
                ).append('\n')
                progress = ((caseIndex * 10 + studyIndex + 1) / 120f).coerceAtMost(0.84f)
            }

            verifyClinicalTrend(clinicalCase, snapshots)
            val latest = PatientPdfExport.exportLatestStudyPdf(
                applicationContext, patient.id, patientDao, rhcDao
            )
            val latestCopy = latest.pdfFile.copyTo(
                File(outputDir, "${clinicalCase.code}_latest.pdf"), overwrite = true
            )
            val longitudinal = LongitudinalComposePdfExporter.export(
                applicationContext, patient.id, patientDao, rhcDao
            )
            val longCopy = longitudinal.file.copyTo(
                File(outputDir, "${clinicalCase.code}_longitudinal.pdf"), overwrite = true
            )
            val latestPages = validatePdf(latestCopy, minimumPages = 1)
            val longPages = validatePdf(longCopy, minimumPages = 12)
            auditLines += "${clinicalCase.code}: 10/10 estudios · tendencia OK · PDF $latestPages+$longPages páginas"
            progress = (0.84f + (caseIndex + 1) / 10f * 0.15f).coerceAtMost(0.99f)
        }

        val patients = patientDao.list(null).first().count { it.internalCode.startsWith("LIVE-") }
        val studies = cases.sumOf { clinicalCase ->
            val patient = checkNotNull(patientDao.getByInternalCode(clinicalCase.code))
            studyDao.listByPatient(patient.id).first().count()
        }
        check(patients == 10) { "Esperaba 10 pacientes LIVE; encontré $patients" }
        check(studies == 100) { "Esperaba 100 estudios LIVE; encontré $studies" }
        File(outputDir, "clinical_manifest.csv").writeText(csv.toString())
        File(outputDir, "audit_result.txt").writeText(
            buildString {
                appendLine("AUDITORÍA CLÍNICA EN VIVO: APROBADA")
                appendLine("Pacientes sintéticos: $patients")
                appendLine("Talleres persistidos: $studies")
                appendLine("PDF validados: 20")
                appendLine()
                auditLines.forEach(::appendLine)
            }
        )
        return "10 pacientes y 100 talleres persistidos. 100/100 snapshots verificados. " +
            "20/20 PDF válidos. Evidencias: ${outputDir.absolutePath}"
    }

    private fun verifySnapshot(r: RhcStudyDataEntity) {
        val co = requireNotNull(r.cardiacOutputSelectedLMin)
        val bsa = requireNotNull(r.bsaM2)
        val expectedPvr = (requireNotNull(r.mpapMmHg) - requireNotNull(r.pawpMmHg)) / co
        val expectedSvr = (requireNotNull(r.mapMmHg) - requireNotNull(r.rapMmHg)) / co
        val expectedPapi = (requireNotNull(r.paspMmHg) - requireNotNull(r.padpMmHg)) / requireNotNull(r.rapMmHg)
        val expectedCpo = requireNotNull(r.mapMmHg) * co / 451.0
        near("PVR", expectedPvr, requireNotNull(r.pvrWood))
        near("PVR dyn", expectedPvr * 80.0, requireNotNull(r.pvrDyn))
        near("SVR", expectedSvr, requireNotNull(r.svrWood))
        near("SVR dyn", expectedSvr * 80.0, requireNotNull(r.svrDyn))
        near("PAPi", expectedPapi, requireNotNull(r.papi))
        near("CPO", expectedCpo, requireNotNull(r.cardiacPowerW))
        near("CI", co / bsa, requireNotNull(r.cardiacIndexSelectedLMinM2))
        check(r::class.java.declaredFields.none { field ->
            field.isAccessible = true
            (field.get(r) as? Double)?.isFinite() == false
        }) { "Snapshot contiene NaN/Infinity" }
    }

    private fun verifyClinicalTrend(case: ClinicalCase, rows: List<RhcStudyDataEntity>) {
        check(rows.size == 10)
        val first = rows.first()
        val last = rows.last()
        val actual = when (case.trendMetric) {
            TrendMetric.PVR -> requireNotNull(last.pvrWood) - requireNotNull(first.pvrWood)
            TrendMetric.CPO -> requireNotNull(last.cardiacPowerW) - requireNotNull(first.cardiacPowerW)
            TrendMetric.RAP -> requireNotNull(last.rapMmHg) - requireNotNull(first.rapMmHg)
            TrendMetric.CO -> requireNotNull(last.cardiacOutputSelectedLMin) - requireNotNull(first.cardiacOutputSelectedLMin)
        }
        when (case.expectedDirection) {
            Direction.UP -> check(actual > 0.15) { "${case.code}: tendencia esperada ascendente" }
            Direction.DOWN -> check(actual < -0.15) { "${case.code}: tendencia esperada descendente" }
            Direction.STABLE -> check(abs(actual) <= 0.15) { "${case.code}: tendencia esperada estable" }
        }

        // Phenotype assertions keep the synthetic cases clinically distinct. They
        // deliberately use current haemodynamic definitions (mPAP > 20 mmHg,
        // PAWP 15 mmHg and PVR 2 WU boundaries).
        val firstPvr = requireNotNull(first.pvrWood)
        val lastPvr = requireNotNull(last.pvrWood)
        when (case.code) {
            "LIVE-001" -> check(rows.all {
                requireNotNull(it.mpapMmHg) <= 20.0 && requireNotNull(it.pvrWood) <= 2.0
            }) { "${case.code}: el control dejó de ser hemodinámicamente normal" }
            "LIVE-002" -> check(rows.all {
                requireNotNull(it.mpapMmHg) > 20.0 && requireNotNull(it.pawpMmHg) <= 15.0 && requireNotNull(it.pvrWood) > 2.0
            }) { "${case.code}: fenotipo precapilar inconsistente" }
            "LIVE-003" -> check(rows.all {
                requireNotNull(it.mpapMmHg) > 20.0 && requireNotNull(it.pawpMmHg) > 15.0 && requireNotNull(it.pvrWood) <= 2.0
            }) { "${case.code}: el caso poscapilar aislado cruzó a combinado" }
            "LIVE-004" -> check(rows.all {
                requireNotNull(it.mpapMmHg) > 20.0 && requireNotNull(it.pawpMmHg) > 15.0 && requireNotNull(it.pvrWood) > 2.0
            }) { "${case.code}: fenotipo combinado inconsistente" }
            "LIVE-005" -> check(requireNotNull(last.rapMmHg) > requireNotNull(first.rapMmHg) &&
                requireNotNull(last.papi) < requireNotNull(first.papi)) { "${case.code}: no reproduce deterioro del VD" }
            "LIVE-006" -> check(requireNotNull(last.cardiacPowerW) > requireNotNull(first.cardiacPowerW)) {
                "${case.code}: no reproduce recuperación de potencia cardíaca"
            }
            "LIVE-007", "LIVE-008" -> check(rows.all { requireNotNull(it.cardiacOutputSelectedLMin) >= 7.0 }) {
                "${case.code}: no conserva el estado de alto gasto"
            }
            "LIVE-009" -> check(lastPvr < firstPvr && lastPvr > 2.0) {
                "${case.code}: respuesta de HAP incoherente"
            }
            "LIVE-010" -> check(requireNotNull(last.pawpMmHg) < requireNotNull(first.pawpMmHg) &&
                requireNotNull(last.rapMmHg) < requireNotNull(first.rapMmHg)) {
                "${case.code}: no reproduce descongestión biventricular"
            }
        }
    }

    private fun validatePdf(file: File, minimumPages: Int): Int {
        check(file.length() > 4_000) { "PDF demasiado pequeño: ${file.name}" }
        check(file.inputStream().use { input -> ByteArray(5).also(input::read).decodeToString() } == "%PDF-")
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer ->
            check(renderer.pageCount >= minimumPages) {
                "${file.name}: ${renderer.pageCount} páginas, mínimo $minimumPages"
            }
            renderer.pageCount
        }
    }

    private fun near(label: String, expected: Double, actual: Double) {
        check(expected.isFinite() && actual.isFinite() && abs(expected - actual) < 1e-8) {
            "$label inválido: esperado=$expected actual=$actual"
        }
    }

    private fun quote(text: String) = "\"${text.replace("\"", "\"\"")}\""

    private data class Values(
        val map: Double, val rap: Double, val pasp: Double, val padp: Double,
        val mpap: Double, val pawp: Double, val co: Double, val hr: Double,
        val hb: Double, val sao2: Double
    )

    private data class ClinicalCase(
        val code: String,
        val displayName: String,
        val label: String,
        val sex: String,
        val birthDateMillis: Long,
        val weightKg: Double,
        val heightCm: Double,
        val trendMetric: TrendMetric,
        val expectedDirection: Direction,
        val values: (Double) -> Values
    )

    private enum class TrendMetric { PVR, CPO, RAP, CO }
    private enum class Direction { UP, DOWN, STABLE }

    private fun clinicalCases(): List<ClinicalCase> = listOf(
        case("LIVE-001", "Caso 01 · Control estable", "Hemodinámica normal estable", "F", 1982, 62.0, 165.0, TrendMetric.PVR, Direction.STABLE) { f ->
            Values(88 + f, 5.0, 28 + f, 10.0, 16 + f * .4, 8.0, 5.5 + f * .05, 70.0, 13.2, 97.0)
        },
        case("LIVE-002", "Caso 02 · HAP progresiva", "HP precapilar progresiva", "F", 1975, 68.0, 168.0, TrendMetric.PVR, Direction.UP) { f ->
            Values(88 - f * 4, 6 + f * 3, 50 + f * 20, 20 + f * 10, 32 + f * 13, 10 + f, 4.8 - f * .6, 78 + f * 8, 14.0, 94 - f * 2)
        },
        case("LIVE-003", "Caso 03 · HFpEF", "HP poscapilar por HFpEF", "F", 1948, 82.0, 160.0, TrendMetric.RAP, Direction.UP) { f ->
            Values(96 - f * 5, 8 + f * 4, 45 + f * 13, 22 + f * 6, 30 + f * 6, 22 + f * 6, 4.8 - f * .6, 76 + f * 7, 12.8, 95.0)
        },
        case("LIVE-004", "Caso 04 · HP combinada", "HP combinada pre/poscapilar", "M", 1958, 76.0, 172.0, TrendMetric.PVR, Direction.UP) { f ->
            Values(90 - f * 5, 10 + f * 3, 62 + f * 14, 28 + f * 7, 38 + f * 10, 20 + f * 4, 4.2 - f * .4, 80 + f * 8, 13.6, 93.0)
        },
        case("LIVE-005", "Caso 05 · Falla VD", "Fallo ventricular derecho progresivo", "M", 1965, 72.0, 175.0, TrendMetric.RAP, Direction.UP) { f ->
            Values(82 - f * 12, 10 + f * 10, 55 - f * 7, 25 + f * 3, 35 + f, 12 + f * 2, 4.0 - f * 1.2, 84 + f * 14, 14.5, 92 - f * 3)
        },
        case("LIVE-006", "Caso 06 · Choque en recuperación", "Choque cardiogénico con recuperación", "M", 1954, 80.0, 178.0, TrendMetric.CPO, Direction.UP) { f ->
            Values(55 + f * 27, 14 - f * 7, 42 - f * 7, 22 - f * 7, 29 - f * 7, 22 - f * 10, 2.2 + f * 2.6, 110 - f * 30, 12.5, 91 + f * 5)
        },
        case("LIVE-007", "Caso 07 · Anemia", "Estado de alto gasto por anemia", "F", 1988, 58.0, 162.0, TrendMetric.CO, Direction.DOWN) { f ->
            Values(78 + f * 6, 5.0, 34 - f * 3, 14 - f * 2, 22 - f * 2, 10.0, 8.5 - f * 1.5, 102 - f * 14, 7.5 + f * 2, 98.0)
        },
        case("LIVE-008", "Caso 08 · Obesidad", "Alto gasto asociado a obesidad", "M", 1979, 130.0, 176.0, TrendMetric.CO, Direction.STABLE) { f ->
            Values(94 - f * 2, 9 - f, 38 - f * 2, 17 - f, 25 - f, 14 - f, 8.0 + f * .05, 88 - f * 4, 15.0, 96.0)
        },
        case("LIVE-009", "Caso 09 · Respuesta HAP", "Respuesta favorable a terapia de HAP", "F", 1990, 64.0, 170.0, TrendMetric.PVR, Direction.DOWN) { f ->
            Values(80 + f * 8, 12 - f * 6, 78 - f * 28, 32 - f * 12, 48 - f * 16, 9.0, 3.5 + f * 1.5, 96 - f * 18, 13.8, 93 + f * 3)
        },
        case("LIVE-010", "Caso 10 · Descongestión", "Sobrecarga biventricular en descongestión", "M", 1951, 90.0, 174.0, TrendMetric.RAP, Direction.DOWN) { f ->
            Values(76 + f * 12, 18 - f * 11, 58 - f * 18, 29 - f * 12, 40 - f * 12, 28 - f * 14, 3.8 + f * 1.2, 92 - f * 16, 12.9, 94 + f * 2)
        }
    )

    private fun case(
        code: String, name: String, label: String, sex: String, birthYear: Int,
        weight: Double, height: Double, metric: TrendMetric, direction: Direction,
        values: (Double) -> Values
    ) = ClinicalCase(
        code, name, label, sex,
        LocalDate.of(birthYear, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        weight, height, metric, direction, values
    )

    private companion object {
        val BASE_STUDY_TIME: Long = LocalDate.of(2024, 1, 15)
            .atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}
