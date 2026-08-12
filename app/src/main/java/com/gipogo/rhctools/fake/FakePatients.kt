package com.gipogo.rhctools.fake

import androidx.annotation.StringRes
import com.gipogo.rhctools.R
import kotlin.random.Random

data class FakePatient(
    val patientId: String,
    val ageSex: String,
    @StringRes val lastStudyRes: Int,
    val tagRes: List<Int>,
    val topInsight: FakeInsight,
    val studies: List<FakeStudy>
)

data class FakeStudy(
    val studyId: String,
    @StringRes val titleRes: Int,
    @StringRes val dateRes: Int,
    @StringRes val contextRes: Int,
    @StringRes val methodRes: Int,
    @StringRes val insightRes: Int
)

data class FakeInsight(
    val kind: Kind,
    @StringRes val textRes: Int
) {
    enum class Kind { Warning, TrendUp, Good, Monitor }
}

object FakePatientsProvider {

    fun samplePatients(): List<FakePatient> {
        // IDs consistentes y repetibles para pruebas
        val p1Studies = listOf(
            FakeStudy(
                studyId = "S-001",
                titleRes = R.string.patient_study_title_rhc,
                dateRes = R.string.patient_study_date_today,
                contextRes = R.string.patient_study_context_followup,
                methodRes = R.string.patient_study_method_fick,
                insightRes = R.string.patient_study_insight_pvr_high
            ),
            FakeStudy(
                studyId = "S-002",
                titleRes = R.string.patient_study_title_rhc,
                dateRes = R.string.patient_study_date_oct_24_2023,
                contextRes = R.string.patient_study_context_baseline,
                methodRes = R.string.patient_study_method_td,
                insightRes = R.string.patient_study_insight_mpap_high
            )
        )

        return listOf(
            FakePatient(
                patientId = "GIP-2024-001",
                ageSex = "62M",
                lastStudyRes = R.string.patients_time_today,
                tagRes = listOf(R.string.patients_tag_pah_eval, R.string.patients_tag_follow_up),
                topInsight = FakeInsight(FakeInsight.Kind.Warning, R.string.patients_insight_pvr_high),
                studies = p1Studies
            ),
            FakePatient(
                patientId = "GIP-2024-002",
                ageSex = "45F",
                lastStudyRes = R.string.patients_time_yesterday,
                tagRes = listOf(R.string.patients_tag_pre_transplant),
                topInsight = FakeInsight(FakeInsight.Kind.TrendUp, R.string.patients_insight_mpap_high),
                studies = listOf(
                    p1Studies.first().copy(studyId = "S-101"),
                )
            ),
            FakePatient(
                patientId = "GIP-2023-089",
                ageSex = "71M",
                lastStudyRes = R.string.patients_time_sep_12_2023,
                tagRes = listOf(R.string.patients_tag_stable, R.string.patients_tag_checkup),
                topInsight = FakeInsight(FakeInsight.Kind.Good, R.string.patients_insight_normal_hemo),
                studies = listOf(
                    p1Studies.last().copy(studyId = "S-201"),
                )
            ),
            FakePatient(
                patientId = "GIP-2024-012",
                ageSex = "58F",
                lastStudyRes = R.string.patients_time_oct_24_2023,
                tagRes = listOf(R.string.patients_tag_post_op),
                topInsight = FakeInsight(FakeInsight.Kind.Monitor, R.string.patients_insight_monitor_ci),
                studies = emptyList()
            )
        )
    }

    // Opcional: generar muchos pacientes para probar scroll/rendimiento
    fun generatePatients(count: Int = 60): List<FakePatient> {
        val base = samplePatients()
        val rand = Random(7)

        return (1..count).map { idx ->
            val template = base[idx % base.size]
            template.copy(
                patientId = "GIP-${2024 + (idx % 2)}-${idx.toString().padStart(3, '0')}",
                ageSex = "${rand.nextInt(18, 90)}" + listOf("M", "F").random(rand),
                studies = template.studies.map { s -> s.copy(studyId = "S-${idx.toString().padStart(3, '0')}-${s.studyId}") }
            )
        }
    }
}
