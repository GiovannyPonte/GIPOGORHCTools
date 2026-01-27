package com.gipogo.rhctools.ui.navigation



sealed class Destinations(val route: String, val label: String) {
    companion object {
        const val NAV_FLAG_SCROLL_TO_EXIT = "nav_flag_scroll_to_exit"
    }
    data object Home : Destinations("home", "Inicio")

    // Graph padre de calculadoras
    data object CalcGraph : Destinations("calc_graph", "Calculos")

    // Rutas internas del graph (calculadoras)
    data object Fick : Destinations("fick", "Fick")
    data object Resistances : Destinations("resistances", "Resistencias")
    data object Cpo : Destinations("cpo", "CPO")
    data object Papi : Destinations("papi", "PAPi")
    data object Pvr : Destinations("pvr", "PVR")

    data object PdfPreview : Destinations("pdf_preview", "PDF")

    // Pacientes (módulo)
    data object Patients : Destinations("patients", "Pacientes")

    data object PatientNew : Destinations("patient_new", "Nuevo paciente")

    data object PatientEdit : Destinations("patient_edit/{patientId}", "Editar paciente") {
        const val ARG_PATIENT_ID = "patientId"
        fun route(patientId: String) = "patient_edit/$patientId"
    }

    data object PatientDetail : Destinations("patient/{patientId}", "Paciente") {
        const val ARG_PATIENT_ID = "patientId"
        fun route(patientId: String) = "patient/$patientId"
    }

    data object StudyNew : Destinations("study_new/{patientId}", "Nuevo estudio") {
        const val ARG_PATIENT_ID = "patientId"
        fun route(patientId: String) = "study_new/$patientId"
    }

    data object StudyDetail : Destinations("study/{patientId}/{studyId}", "Estudio") {
        const val ARG_PATIENT_ID = "patientId"
        const val ARG_STUDY_ID = "studyId"
        fun route(patientId: String, studyId: String) = "study/$patientId/$studyId"
    }
    // Home de herramientas (taller hemodinámico)
    data object Calculators : Destinations("calculators", "Calculators")
    data object ReportRender : Destinations(
        route = "report_render/{patientId}",
        label = "Report Render"
    ) {
        const val ARG_PATIENT_ID = "patientId"
        fun route(patientId: String) = "report_render/$patientId"
    }

}
