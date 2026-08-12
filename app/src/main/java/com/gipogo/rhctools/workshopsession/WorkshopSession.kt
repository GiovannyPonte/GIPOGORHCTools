package com.gipogo.rhctools.workshop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class WorkshopMode { QUICK, PATIENT_STUDY }

data class WorkshopContext(
    val mode: WorkshopMode = WorkshopMode.QUICK,
    // ⚠️ NO guardar nombre, edad, etc. Solo IDs internos.
    val patientId: String? = null,
    val studyId: String? = null,

    /**
     * Identificador de "sesión" del taller.
     * - Cambia cada vez que se inicia un taller nuevo (Quick o Patient Study)
     * - Permite resetear UI/VM sin depender de studyId (que no existe en QUICK)
     */
    val workshopRunId: String = UUID.randomUUID().toString()
)

object WorkshopSession {
    private val _context = MutableStateFlow(WorkshopContext())
    val context: StateFlow<WorkshopContext> = _context

    private fun newRunId(): String = UUID.randomUUID().toString()

    fun startQuick() {
        _context.value = WorkshopContext(
            mode = WorkshopMode.QUICK,
            patientId = null,
            studyId = null,
            workshopRunId = newRunId()
        )
    }

    /**
     * Llamar cuando el taller está vinculado a un paciente y un Study (RHC).
     * Cada llamada genera un workshopRunId nuevo para "resetear" el taller.
     */
    fun startPatientStudy(patientId: String, studyId: String) {
        _context.value = WorkshopContext(
            mode = WorkshopMode.PATIENT_STUDY,
            patientId = patientId,
            studyId = studyId,
            workshopRunId = newRunId()
        )
    }

    fun clear() {
        _context.value = WorkshopContext(workshopRunId = newRunId())
    }
}
