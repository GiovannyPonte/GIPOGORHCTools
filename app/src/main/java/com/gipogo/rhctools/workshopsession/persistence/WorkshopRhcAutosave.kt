package com.gipogo.rhctools.workshop.persistence

import android.content.Context
import android.util.Log
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

object WorkshopRhcAutosave {

    data class Status(
        val enabled: Boolean = false,
        val isSaving: Boolean = false,
        val lastSavedAtMillis: Long? = null,
        val lastError: String? = null
    )

    private val _status = MutableStateFlow(Status())
    val status = _status

    @Volatile private var started = false
    private var pending: Job? = null

    // --------------------------
    // coMethod controlado desde UI (FICK/TD)
    // --------------------------
    @Volatile private var currentCoMethod: String? = null

    fun setCoMethod(method: String) {
        currentCoMethod = when (method.uppercase()) {
            "TD" -> "TD"
            "FICK" -> "FICK"
            else -> "FICK"
        }
    }

    fun clearCoMethod() {
        currentCoMethod = null
    }

    /**
     * Llamar 1 vez (ideal: AppNavGraph).
     * Observa ReportStore.entries y guarda con debounce solo si está en modo PATIENT_STUDY con studyId.
     */
    fun start(context: Context, scope: CoroutineScope, debounceMs: Long = 500L) {
        if (started) return
        started = true

        val appCtx = context.applicationContext
        val db = DbProvider.get(appCtx)
        val rhcDao = db.rhcStudyDao()

        scope.launch(Dispatchers.IO) {
            ReportStore.entries.collectLatest {
                val ctx = WorkshopSession.context.value
                val enabled = (ctx.mode == WorkshopMode.PATIENT_STUDY && !ctx.studyId.isNullOrBlank())

                if (!enabled) {
                    _status.value = _status.value.copy(enabled = false)
                    return@collectLatest
                }

                _status.value = _status.value.copy(enabled = true)

                pending?.cancel()
                pending = scope.launch(Dispatchers.IO) {
                    delay(debounceMs)
                    doSave(appCtx, rhcDao)
                }
            }
        }
    }

    /**
     * ✅ Guardado inmediato (sin debounce).
     * Úsalo justo después de cada cálculo (ReportStore.upsert).
     */
    fun flushNow(context: Context, scope: CoroutineScope) {
        val appCtx = context.applicationContext
        val db = DbProvider.get(appCtx)
        val rhcDao = db.rhcStudyDao()

        pending?.cancel()
        pending = null

        scope.launch(Dispatchers.IO) {
            doSave(appCtx, rhcDao)
        }
    }

    // --------------------------
    // Helpers
    // --------------------------

    private fun readD(key: String): Double? =
        ReportStore.latestValueDoubleByKey(key)

    private fun readS(key: String): String? =
        ReportStore.latestValueStringByKey(key)

    private fun normalizeUnitsToken(raw: String?): String? {
        val v = raw?.trim()?.uppercase() ?: return null
        if (v.isBlank()) return null
        return when (v) {
            "WU", "WOOD", "WOODS", "WOOD_UNITS", "WOODUNITS" -> "WOOD"
            "DYN", "DYNES", "DYNE", "CGS", "DYN_S_CM5", "DYN·S·CM⁻⁵", "DYNS/CM5" -> "DYN"
            else -> v // si tu UI usa otra convención, la conservamos
        }
    }

    /**
     * Interno: guarda snapshot 100% auditable (inputs + outputs + unidades)
     */
    private suspend fun doSave(appCtx: Context, rhcDao: RhcStudyDao) {
        try {
            val ctx = WorkshopSession.context.value
            val studyId = ctx.studyId

            val enabled = (ctx.mode == WorkshopMode.PATIENT_STUDY && !studyId.isNullOrBlank())
            if (!enabled) {
                _status.value = _status.value.copy(enabled = false)
                return
            }

            _status.value = _status.value.copy(isSaving = true, lastError = null)

            val now = System.currentTimeMillis()

            // --------------------------
            // Prefill (peso/talla) desde WorkshopPrefillStore (snapshot)
            // --------------------------
            val prefill = WorkshopPrefillStore.prefill.value
            val weightKg = prefill.weightKg
            val heightCm = prefill.heightCm

            // --------------------------
            // Core flows
            // --------------------------
            val coLMin = readD(SharedKeys.CO_LMIN)
            val bsaM2 = readD(SharedKeys.BSA_M2)

            // CI: preferir key si existe, si no, derivarlo desde CO/BSA
            val ciFromKey = readD(SharedKeys.CI_LMIN_M2)
            val ci = ciFromKey ?: run {
                if (coLMin != null && bsaM2 != null && bsaM2 > 0.0) (coLMin / bsaM2) else null
            }

            // --------------------------
            // Fick inputs (auditoría)
            // --------------------------
            val saO2Percent = readD(SharedKeys.SAO2_PERCENT)
            val svO2Percent = readD(SharedKeys.SVO2_PERCENT)
            val hemoglobinGdl = readD(SharedKeys.HB_GDL)
            val heartRateBpm = readD(SharedKeys.HR_BPM)
            val vo2MlMin = readD(SharedKeys.VO2_MLMIN)
            val vo2Mode = readS(SharedKeys.VO2_MODE)?.takeIf { it.isNotBlank() }

            // --------------------------
            // Pressures (mmHg)
            // --------------------------
            val mapMmHg = readD(SharedKeys.MAP_MMHG)

            // RAP preferente; si no existe, fallback a CVP
            val rapMmHg = readD(SharedKeys.RAP_MMHG)
                ?: readD(SharedKeys.CVP_MMHG)

            val paspMmHg = readD(SharedKeys.PASP_MMHG)
            val padpMmHg = readD(SharedKeys.PADP_MMHG)
            val mpapMmHg = readD(SharedKeys.MPAP_MMHG)
            val pawpMmHg = readD(SharedKeys.PAWP_MMHG)

            // --------------------------
            // Derived outputs (SVR/PVR/PAPi/CPO/CPI)
            // --------------------------
            val svrWood = readD(SharedKeys.SVR_WOOD)
            val svrDyn = readD(SharedKeys.SVR_DYN)
            val svrUnits = normalizeUnitsToken(readS(SharedKeys.SVR_UNITS))

            val pvrWood = readD(SharedKeys.PVR_WOOD)
            val pvrDyn = readD(SharedKeys.PVR_DYN)
            val pvrUnits = normalizeUnitsToken(readS(SharedKeys.PVR_UNITS))

            val papi = readD(SharedKeys.PAPI)

            val cardiacPowerW = readD(SharedKeys.CPO_W)
            val cardiacPowerIndexWm2 = readD(SharedKeys.CPI_W_M2)

            // --------------------------
            // coMethod (prioridad: UI -> key -> default)
            // --------------------------
            val coMethod = currentCoMethod
                ?: readS(SharedKeys.CO_METHOD)?.uppercase()
                ?: "FICK"

            // --------------------------
            // Construcción de entidad (1 fila por studyId, upsert por DAO)
            // --------------------------
            val entity = RhcStudyDataEntity(
                id = UUID.randomUUID().toString(), // DAO preserva id si ya existe fila
                studyId = studyId!!,

                // Anthropometrics
                weightKg = weightKg,
                heightCm = heightCm,
                bsaM2 = bsaM2,

                // Fick inputs
                saO2Percent = saO2Percent,
                svO2Percent = svO2Percent,
                hemoglobinGdl = hemoglobinGdl,
                heartRateBpm = heartRateBpm,
                vo2MlMin = vo2MlMin,
                vo2Mode = vo2Mode,

                // Pressures
                mapMmHg = mapMmHg,
                rapMmHg = rapMmHg,
                paspMmHg = paspMmHg,
                padpMmHg = padpMmHg,
                mpapMmHg = mpapMmHg,
                pawpMmHg = pawpMmHg,

                // Outputs
                cardiacOutputLMin = coLMin,
                cardiacIndexLMinM2 = ci,

                // Derived
                svrWood = svrWood,
                svrDyn = svrDyn,
                pvrWood = pvrWood,
                pvrDyn = pvrDyn,
                papi = papi,
                cardiacPowerW = cardiacPowerW,
                cardiacPowerIndexWm2 = cardiacPowerIndexWm2,

                // Units / config
                svrUnits = svrUnits,
                pvrUnits = pvrUnits,
                coMethod = coMethod,

                // Audit
                createdAtMillis = now,  // DAO preserva createdAt original si ya existía
                updatedAtMillis = now
            )

            rhcDao.upsertByStudyId(entity)

            _status.value = Status(
                enabled = true,
                isSaving = false,
                lastSavedAtMillis = now,
                lastError = null
            )
        } catch (t: Throwable) {
            Log.e("WorkshopRhcAutosave", "Autosave failed", t)
            _status.value = _status.value.copy(
                isSaving = false,
                lastError = t.message ?: "Autosave failed"
            )
        }
    }
}
