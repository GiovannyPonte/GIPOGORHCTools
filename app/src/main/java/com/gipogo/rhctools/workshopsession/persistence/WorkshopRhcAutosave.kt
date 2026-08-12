package com.gipogo.rhctools.workshop.persistence

import android.content.Context
import android.util.Log
import com.gipogo.rhctools.data.db.DbProvider
import com.gipogo.rhctools.data.db.dao.RhcStudyDao
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.domain.CanonicalHemodynamics
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.report.SharedKeys
import com.gipogo.rhctools.workshop.WorkshopMode
import com.gipogo.rhctools.workshop.WorkshopPrefillStore
import com.gipogo.rhctools.workshop.WorkshopSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

object WorkshopRhcAutosave {

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object NotApplicable : SaveResult
        data class Failure(val code: String, val cause: Throwable) : SaveResult
    }

    data class Status(
        val enabled: Boolean = false,
        val isSaving: Boolean = false,
        val lastSavedAtMillis: Long? = null,
        val lastError: String? = null
    )

    private val _status = MutableStateFlow(Status())
    val status = _status

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectorJob: Job? = null
    private var pending: Job? = null

    // --------------------------
    // coMethod controlado desde UI (FICK/TD)
    // --------------------------
    @Volatile private var currentCoMethod: String? = null

    fun setCoMethod(method: String) {
        currentCoMethod = when (method.trim().uppercase()) {
            "TD" -> "TD"
            "FICK" -> "FICK"
            else -> "FICK"
        }
    }

    fun clearCoMethod() {
        currentCoMethod = null
    }

    // --------------------------
    // coSelectionReason controlado desde UI (p.ej. USER_SELECTED)
    // --------------------------
    @Volatile private var currentCoSelectionReason: String? = null

    /**
     * Permite que la UI marque explícitamente la razón de selección del CO activo.
     * Ejemplos: USER_SELECTED, LAST_CALCULATED, ONLY_ONE_AVAILABLE, AUTO_DEFAULT.
     *
     * Regla conservadora:
     * - se normaliza a uppercase
     * - si viene vacío -> null
     * - se limpia automáticamente tras doSave (finally)
     */
    fun setCoSelectionReason(reason: String?) {
        currentCoSelectionReason = reason?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    }

    fun clearCoSelectionReason() {
        currentCoSelectionReason = null
    }

    /**
     * Llamar 1 vez (ideal: AppNavGraph).
     * Observa ReportStore.entries y guarda con debounce solo si está en modo PATIENT_STUDY con studyId.
     */
    @Synchronized
    fun start(context: Context, debounceMs: Long = 500L) {
        if (collectorJob?.isActive == true) return
        val appCtx = context.applicationContext

        collectorJob = applicationScope.launch {
            ReportStore.entries.collectLatest {
                val ctx = WorkshopSession.context.value
                val enabled = (ctx.mode == WorkshopMode.PATIENT_STUDY && !ctx.studyId.isNullOrBlank())

                if (!enabled) {
                    _status.value = _status.value.copy(enabled = false)
                    return@collectLatest
                }

                _status.value = _status.value.copy(enabled = true)

                pending?.cancel()
                pending = applicationScope.launch {
                    delay(debounceMs)
                    val db = when (val result = DbProvider.getResult(appCtx)) {
                        is DbProvider.DbOpenResult.Success -> result.db
                        is DbProvider.DbOpenResult.Failure -> {
                            _status.value = _status.value.copy(
                                isSaving = false,
                                lastError = result.error.message ?: "Database unavailable"
                            )
                            return@launch
                        }
                    }
                    doSave(appCtx, db.rhcStudyDao())
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
        pending?.cancel()
        pending = null

        scope.launch(Dispatchers.IO) {
            val db = when (val result = DbProvider.getResult(appCtx)) {
                is DbProvider.DbOpenResult.Success -> result.db
                is DbProvider.DbOpenResult.Failure -> {
                    _status.value = _status.value.copy(
                        isSaving = false,
                        lastError = result.error.message ?: "Database unavailable"
                    )
                    return@launch
                }
            }
            doSave(appCtx, db.rhcStudyDao())
        }
    }

    /**
     * Variante suspend para flujos de salida:
     * asegura que el snapshot se persista antes de limpiar memoria/navegar.
     */
    suspend fun flushNowAndWait(context: Context): SaveResult {
        val appCtx = context.applicationContext
        pending?.cancel()
        pending = null

        val db = when (val result = DbProvider.getResult(appCtx)) {
            is DbProvider.DbOpenResult.Success -> result.db
            is DbProvider.DbOpenResult.Failure -> {
                _status.value = _status.value.copy(
                    isSaving = false,
                    lastError = ERROR_DATABASE_UNAVAILABLE
                )
                return SaveResult.Failure(ERROR_DATABASE_UNAVAILABLE, result.error)
            }
        }
        return doSave(appCtx, db.rhcStudyDao())
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
    private suspend fun doSave(appCtx: Context, rhcDao: RhcStudyDao): SaveResult {
        try {
            val ctx = WorkshopSession.context.value
            val studyId = ctx.studyId

            val enabled = (ctx.mode == WorkshopMode.PATIENT_STUDY && !studyId.isNullOrBlank())
            if (!enabled) {
                _status.value = _status.value.copy(enabled = false)
                return SaveResult.NotApplicable
            }

            _status.value = _status.value.copy(isSaving = true, lastError = null)

            val now = System.currentTimeMillis()

            // --------------------------
            // Prefill (peso/talla) desde WorkshopPrefillStore (snapshot)
            // --------------------------
            val prefill = WorkshopPrefillStore.prefill.value
            val weightKg = prefill.weightKg
            val heightCm = prefill.heightCm

            // Fallback opcional desde ReportStore (si algún día publicas estas keys)
            val weightKgFromKeys = readD(SharedKeys.WEIGHT_KG)?.takeIf { it.isFinite() && it > 0.0 }
            val heightCmFromKeys = readD(SharedKeys.HEIGHT_CM)?.takeIf { it.isFinite() && it > 0.0 }

            // --------------------------
            // Core flows
            // --------------------------
            val coLMin = readD(SharedKeys.CO_LMIN)
            val bsaM2 = readD(SharedKeys.BSA_M2)

            // CI: preferir key si existe, si no, derivarlo desde CO/BSA
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
            val mapMmHg = readD(SharedKeys.MAP_MMHG)?.takeIf { it.isFinite() }

            val rapMmHg = (readD(SharedKeys.RAP_MMHG) ?: readD(SharedKeys.CVP_MMHG))
                ?.takeIf { it.isFinite() }

            val paspMmHg = readD(SharedKeys.PASP_MMHG)
            val padpMmHg = readD(SharedKeys.PADP_MMHG)
            val mpapMmHg = readD(SharedKeys.MPAP_MMHG)
            val pawpMmHg = readD(SharedKeys.PAWP_MMHG)

            // Unit tokens are presentation preferences only. Numeric values are
            // recalculated below from canonical inputs before persistence.
            val svrUnits = normalizeUnitsToken(readS(SharedKeys.SVR_UNITS))
            val pvrUnits = normalizeUnitsToken(readS(SharedKeys.PVR_UNITS))

            // --------------------------
            // coMethod (prioridad: UI -> key -> default)
            // --------------------------
            val coMethod = currentCoMethod
                ?: readS(SharedKeys.CO_METHOD)?.uppercase()
                ?: "FICK"

            // --------------------------
            // Construcción de entidad (1 fila por studyId, upsert por DAO)
            // ✅ Regla: coexistencia FICK + TD sin pisarse.
            // --------------------------

            // Snapshot previo para preservar el método alterno y/o selected si hoy no hay CO válido
            val existing = rhcDao.getByStudyId(studyId!!)

            val coValid = coLMin?.takeIf { it.isFinite() && it > 0.0 }
            val bsaValid = bsaM2?.takeIf { it.isFinite() && it > 0.0 }

            // CI is always derived from the same canonical CO and BSA that will
            // be persisted. Never trust a presentation value or an older method.
            val ciValid = if (coValid != null && bsaValid != null) {
                (coValid / bsaValid).takeIf { it.isFinite() && it > 0.0 }
            } else null

            val normalizedCoMethod = when (coMethod.uppercase()) {
                "TD" -> "TD"
                "FICK" -> "FICK"
                else -> "FICK"
            }

            // Preservar valores previos del método alterno
            val prevFickCo = existing?.cardiacOutputFickLMin
            val prevFickCi = existing?.cardiacIndexFickLMinM2
            val prevTdCo = existing?.cardiacOutputTdLMin
            val prevTdCi = existing?.cardiacIndexTdLMinM2

            val newFickCo = if (normalizedCoMethod == "FICK") coValid else prevFickCo
            val newFickCi = if (normalizedCoMethod == "FICK") ciValid else prevFickCi

            val newTdCo = if (normalizedCoMethod == "TD") coValid else prevTdCo
            val newTdCi = if (normalizedCoMethod == "TD") ciValid else prevTdCi

            // Selected:
            // - Si hoy hay CO válido -> default: el método recién calculado
            // - Si hoy no hay CO válido -> conservar selected previo (fail-clean)
            val selectedMethod = if (coValid != null) {
                if (normalizedCoMethod == "TD") "TD" else "FICK"
            } else {
                existing?.coSelectedMethod ?: existing?.coMethod ?: "FICK"
            }

            val selectedCo = if (coValid != null) {
                coValid
            } else {
                existing?.cardiacOutputSelectedLMin ?: existing?.cardiacOutputLMin
            }

            val selectionReason = currentCoSelectionReason ?: (if (coValid != null) {
                "LAST_CALCULATED"
            } else {
                existing?.coSelectionReason
            } ?: "AUTO_DEFAULT")

            // Recalculate every derived output from canonical inputs and the
            // selected CO. This prevents mixing a new Fick/TD result with stale
            // resistances or cardiac power calculated using another CO.
            val selectedCoValid = selectedCo?.takeIf { it.isFinite() && it > 0.0 }
            val derived = CanonicalHemodynamics.derive(
                cardiacOutputLMin = selectedCoValid,
                bsaM2 = bsaValid,
                mapMmHg = mapMmHg,
                rapMmHg = rapMmHg,
                paspMmHg = paspMmHg,
                padpMmHg = padpMmHg,
                mpapMmHg = mpapMmHg,
                pawpMmHg = pawpMmHg
            )

            val entity = RhcStudyDataEntity(
                id = UUID.randomUUID().toString(), // DAO preserva id si ya existe fila
                studyId = studyId,

                // Anthropometrics
                weightKg = weightKg ?: weightKgFromKeys,
                heightCm = heightCm ?: heightCmFromKeys,
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

                // ✅ Per-method outputs
                cardiacOutputFickLMin = newFickCo,
                cardiacIndexFickLMinM2 = newFickCi,
                cardiacOutputTdLMin = newTdCo,
                cardiacIndexTdLMinM2 = newTdCi,

                // ✅ Selected outputs
                cardiacOutputSelectedLMin = selectedCo,
                cardiacIndexSelectedLMinM2 = derived.cardiacIndexLMinM2,
                coSelectedMethod = selectedMethod,
                coSelectionReason = selectionReason,

                // ✅ Legacy (compatibilidad): reflejar SIEMPRE selected
                cardiacOutputLMin = selectedCo,
                cardiacIndexLMinM2 = derived.cardiacIndexLMinM2,

                // Derived
                svrWood = derived.svrWood,
                svrDyn = derived.svrDyn,
                pvrWood = derived.pvrWood,
                pvrDyn = derived.pvrDyn,
                papi = derived.papi,
                cardiacPowerW = derived.cardiacPowerW,
                cardiacPowerIndexWm2 = derived.cardiacPowerIndexWm2,

                // Units / config
                svrUnits = svrUnits,
                pvrUnits = pvrUnits,
                coMethod = selectedMethod, // legacy

                // Audit
                createdAtMillis = now,  // DAO preserva createdAt original si ya existía
                updatedAtMillis = now
            )

            rhcDao.upsertByStudyId(entity)
            checkNotNull(rhcDao.getByStudyId(studyId)) {
                "El estudio no pudo verificarse después de guardarlo"
            }

            _status.value = Status(
                enabled = true,
                isSaving = false,
                lastSavedAtMillis = now,
                lastError = null
            )
            return SaveResult.Saved
        } catch (t: Throwable) {
            Log.e("WorkshopRhcAutosave", "Autosave failed: ${t.javaClass.simpleName}")
            _status.value = _status.value.copy(
                isSaving = false,
                lastError = ERROR_WRITE_FAILED
            )
            return SaveResult.Failure(ERROR_WRITE_FAILED, t)
        } finally {
            // Evitar que la razón "se filtre" a futuros flushes.
            currentCoSelectionReason = null
        }
    }

    private const val ERROR_DATABASE_UNAVAILABLE = "DB_UNAVAILABLE"
    private const val ERROR_WRITE_FAILED = "WRITE_FAILED"
}
