package com.gipogo.rhctools.workshopsession.persistence

import android.content.Context
import android.util.Log
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.workshop.WorkshopMode
import com.gipogo.rhctools.workshop.WorkshopPrefillStore
import com.gipogo.rhctools.workshop.WorkshopSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Persistencia mínima y robusta del taller RHC:
 * - Guarda CO (L/min), BSA (m²), CI (L/min/m²) y coMethod (FICK/TD)
 * - Usa SOLO APIs reales existentes: ReportStore.latestValueDoubleByKey(...)
 * - No depende de SharedKeys adicionales (MAP/RAP/etc) hasta que las confirmemos.
 */
object WorkshopRhcPersistence {

    data class Status(
        val enabled: Boolean = false,
        val isSaving: Boolean = false,
        val lastSavedAtMillis: Long? = null,
        val lastError: String? = null
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    @Volatile private var started = false
    private var pending: Job? = null

    // coMethod: setear desde FickScreen cuando calculas (FICK o TD)
    @Volatile private var currentCoMethod: String = "FICK"

    fun setCoMethod(method: String) {
        currentCoMethod = when (method.uppercase()) {
            "TD" -> "TD"
            "FICK" -> "FICK"
            else -> "FICK"
        }
    }

    /**
     * Llamar 1 vez (por ejemplo en HomeCalculatorScreen o AppNavGraph al entrar al módulo).
     * Observa ReportStore.entries y guarda con debounce si está en modo PATIENT_STUDY con studyId.
     */
    fun start(context: Context, scope: CoroutineScope, debounceMs: Long = 500L) {
        if (started) return
        started = true

        val appCtx = context.applicationContext

        scope.launch(Dispatchers.IO) {
            ReportStore.entries.collect { _ ->
                val ctx = WorkshopSession.context.value
                val enabled = (ctx.mode == WorkshopMode.PATIENT_STUDY && !ctx.studyId.isNullOrBlank())
                if (!enabled) {
                    _status.value = _status.value.copy(enabled = false)
                    return@collect
                }

                _status.value = _status.value.copy(enabled = true)

                // Debounce
                pending?.cancel()
                pending = scope.launch(Dispatchers.IO) {
                    delay(debounceMs)
                    saveOnce(appCtx)
                }
            }
        }
    }

    /**
     * Guardado inmediato (sin debounce) para usar en back/exit si quieres.
     */
    fun flushNow(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { saveOnce(context.applicationContext) }
    }

    private suspend fun saveOnce(appCtx: Context) {
        try {
            val ctx = WorkshopSession.context.value
            val studyId = ctx.studyId
            if (ctx.mode != WorkshopMode.PATIENT_STUDY || studyId.isNullOrBlank()) return

            _status.value = _status.value.copy(isSaving = true, lastError = null)

            val db = DbProvider.get(appCtx)
            val rhcDao = db.rhcStudyDao()

            val now = System.currentTimeMillis()

            // ✅ APIs reales existentes en ReportStore
            val coLMin = ReportStore.latestValueDoubleByKey(SharedKeys.CO_LMIN)
            val bsaM2 = ReportStore.latestValueDoubleByKey(SharedKeys.BSA_M2)

            val ci = if (coLMin != null && bsaM2 != null && bsaM2 > 0.0) {
                coLMin / bsaM2
            } else null

            // Prefill del paciente (peso/talla) si está disponible
            val prefill = WorkshopPrefillStore.prefill.value
            val weightKg = prefill.weightKg
            val heightCm = prefill.heightCm

            val existing = rhcDao.getByStudyId(studyId)

            val entity = RhcStudyDataEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                studyId = studyId,

                weightKg = weightKg,
                heightCm = heightCm,
                bsaM2 = bsaM2,

                cardiacOutputLMin = coLMin,
                cardiacIndexLMinM2 = ci,

                // coMethod (FICK / TD)
                coMethod = currentCoMethod,

                // timestamps
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )

            if (existing == null) rhcDao.insert(entity) else rhcDao.update(entity)

            _status.value = Status(
                enabled = true,
                isSaving = false,
                lastSavedAtMillis = now,
                lastError = null
            )
        } catch (t: Throwable) {
            Log.e("WorkshopRhcPersistence", "saveOnce failed", t)
            _status.value = _status.value.copy(isSaving = false, lastError = t.message ?: "Save failed")
        }
    }
}
