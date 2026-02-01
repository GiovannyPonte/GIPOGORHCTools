package com.gipogo.rhctools.report

import com.gipogo.rhctools.util.Format

/**
 * Centraliza el armado y persistencia de CalcEntry con KEYS canónicas.
 *
 * REGLAS:
 * 1) Esta capa NO calcula fórmulas clínicas.
 * 2) Solo arma payload auditable (inputs/outputs con SharedKeys cuando aplica).
 * 3) No debe persistir NaN/Inf: si un valor no es finito, se omite o se corta el upsert.
 */
object CalcEntryWriters {

    // ---------------------------------------------------------------------
    // Helpers internos
    // ---------------------------------------------------------------------

    private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
    private fun Double?.finitePositiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

    // ---------------------------------------------------------------------
    // CPO / CPI
    // ---------------------------------------------------------------------

    fun upsertCpo(
        timestampMillis: Long,
        title: String,
        mapMmHg: Double?,
        coLMin: Double?,
        bsaM2: Double?,
        cpoW: Double,
        cpiWm2: Double?
    ) {
        val cpo = cpoW.takeIf { it.isFinite() } ?: return
        val cpi = cpiWm2.finiteOrNull()

        val inputs = listOf(
            LineItem(
                key = SharedKeys.MAP_MMHG,
                label = "MAP",
                value = mapMmHg.finitePositiveOrNull()?.let { Format.d(it, 0) } ?: "",
                unit = "mmHg",
                detail = "Mean Arterial Pressure"
            ),
            LineItem(
                key = SharedKeys.CO_LMIN,
                label = "CO",
                value = coLMin.finitePositiveOrNull()?.let { Format.d(it, 2) } ?: "",
                unit = "L/min",
                detail = "Cardiac Output"
            ),
            LineItem(
                key = SharedKeys.BSA_M2,
                label = "BSA",
                value = bsaM2.finitePositiveOrNull()?.let { Format.d(it, 2) } ?: "",
                unit = "m²",
                detail = "Body Surface Area"
            )
        )

        val outputs = listOfNotNull(
            LineItem(
                key = SharedKeys.CPO_W,
                label = "CPO",
                value = Format.d(cpo, 2),
                unit = "W",
                detail = "Cardiac Power Output"
            ),
            cpi?.let {
                LineItem(
                    key = SharedKeys.CPI_W_M2,
                    label = "CPI",
                    value = Format.d(it, 2),
                    unit = "W/m²",
                    detail = "Cardiac Power Index"
                )
            }
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.CPO,
                timestampMillis = timestampMillis,
                title = title,
                inputs = inputs,
                outputs = outputs
            )
        )
    }

    // ---------------------------------------------------------------------
    // SVR
    // ---------------------------------------------------------------------

    fun upsertSvr(
        timestampMillis: Long,
        title: String,
        mapText: String,
        cvpText: String,
        coText: String,
        svrWu: Double?,
        svrDyn: Double?,
        svrUnits: String // "WOOD" | "DYN"
    ) {
        val wu = svrWu.finiteOrNull() ?: return
        val dyn = svrDyn.finiteOrNull() ?: return

        val outputs = listOf(
            LineItem(
                key = SharedKeys.SVR_WOOD,
                label = "SVR",
                value = Format.d(wu, 2),
                unit = "WU",
                detail = "Wood Units"
            ),
            LineItem(
                key = SharedKeys.SVR_DYN,
                label = "SVR",
                value = Format.d(dyn, 0),
                unit = "dyn·s·cm⁻⁵",
                detail = "CGS units"
            ),
            LineItem(
                key = SharedKeys.SVR_UNITS,
                label = "SVR units",
                value = svrUnits,
                unit = null,
                detail = null
            )
        )

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.SVR,
                timestampMillis = timestampMillis,
                title = title,
                inputs = listOf(
                    LineItem(
                        key = SharedKeys.MAP_MMHG,
                        label = "MAP",
                        value = mapText,
                        unit = "mmHg",
                        detail = "Mean Arterial Pressure"
                    ),
                    LineItem(
                        key = SharedKeys.CVP_MMHG,
                        label = "CVP",
                        value = cvpText,
                        unit = "mmHg",
                        detail = "Central Venous Pressure"
                    ),
                    LineItem(
                        key = SharedKeys.CO_LMIN,
                        label = "CO",
                        value = coText,
                        unit = "L/min",
                        detail = "Cardiac Output"
                    )
                ),
                outputs = outputs
            )
        )
    }

    // ---------------------------------------------------------------------
    // PAPi (+ PAPP opcional sin key)
    // ---------------------------------------------------------------------

    fun upsertPapi(
        timestampMillis: Long,
        title: String,
        paspText: String,
        padpText: String,
        rapText: String,
        papi: Double?,
        pappMmHg: Double?
    ) {
        val papiValue = papi.finiteOrNull() ?: return
        val papp = pappMmHg.finiteOrNull()

        val outputs = buildList {
            add(
                LineItem(
                    key = SharedKeys.PAPI,
                    label = "PAPi",
                    value = Format.d(papiValue, 2),
                    unit = "",
                    detail = "Pulmonary Artery Pulsatility Index"
                )
            )
            if (papp != null) {
                add(
                    LineItem(
                        label = "PAPP",
                        value = Format.d(papp, 1),
                        unit = "mmHg",
                        detail = "Pulmonary artery pulse pressure"
                    )
                )
            }
        }

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PAPI,
                timestampMillis = timestampMillis,
                title = title,
                inputs = listOf(
                    LineItem(
                        key = SharedKeys.PASP_MMHG,
                        label = "PASP",
                        value = paspText,
                        unit = "mmHg",
                        detail = "PA systolic pressure"
                    ),
                    LineItem(
                        key = SharedKeys.PADP_MMHG,
                        label = "PADP",
                        value = padpText,
                        unit = "mmHg",
                        detail = "PA diastolic pressure"
                    ),
                    LineItem(
                        key = SharedKeys.RAP_MMHG,
                        label = "RAP",
                        value = rapText,
                        unit = "mmHg",
                        detail = "Right atrial pressure"
                    )
                ),
                outputs = outputs
            )
        )
    }

    // ---------------------------------------------------------------------
    // PVR (+ TPR opcional sin key)
    // ---------------------------------------------------------------------

    fun upsertPvr(
        timestampMillis: Long,
        title: String,
        mpapText: String,
        pawpText: String,
        coText: String,
        outputUnits: String, // "WOOD" | "DYN"
        pvrWu: Double?,
        pvrDyn: Double?,
        tprWu: Double?,
        tprDyn: Double?
    ) {
        // Regla actual en tu Screen: guardar si hay PVR o TPR.
        if (pvrWu == null && tprWu == null) return

        val outputs = mutableListOf<LineItem>()

        // PVR con KEYS (auditables en BD)
        val wu = pvrWu.finiteOrNull()
        val dyn = pvrDyn.finiteOrNull()
        if (wu != null && dyn != null) {
            outputs += LineItem(
                key = SharedKeys.PVR_WOOD,
                label = "PVR",
                value = Format.d(wu, 2),
                unit = "WU",
                detail = "Pulmonary Vascular Resistance (Wood Units)"
            )
            outputs += LineItem(
                key = SharedKeys.PVR_DYN,
                label = "PVR",
                value = Format.d(dyn, 0),
                unit = "dyn·s·cm⁻⁵",
                detail = "Pulmonary Vascular Resistance (CGS)"
            )
            outputs += LineItem(
                key = SharedKeys.PVR_UNITS,
                label = "PVR units",
                value = outputUnits,
                unit = null,
                detail = null
            )
        }

        // TPR (sin keys; no hay columnas dedicadas en BD)
        val tWu = tprWu.finiteOrNull()
        val tDyn = tprDyn.finiteOrNull()
        if (tWu != null && tDyn != null) {
            outputs += LineItem(
                label = "TPR",
                value = Format.d(tWu, 2),
                unit = "WU",
                detail = "Total Pulmonary Resistance (Wood Units)"
            )
            outputs += LineItem(
                label = "TPR",
                value = Format.d(tDyn, 0),
                unit = "dyn·s·cm⁻⁵",
                detail = "Total Pulmonary Resistance (CGS)"
            )
        }

        if (outputs.isEmpty()) return

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.PVR,
                timestampMillis = timestampMillis,
                title = title,
                inputs = listOf(
                    LineItem(key = SharedKeys.MPAP_MMHG, label = "mPAP", value = mpapText, unit = "mmHg", detail = "Mean Pulmonary Artery Pressure"),
                    LineItem(key = SharedKeys.PAWP_MMHG, label = "PAWP", value = pawpText, unit = "mmHg", detail = "Pulmonary Artery Wedge Pressure"),
                    LineItem(key = SharedKeys.CO_LMIN, label = "CO", value = coText, unit = "L/min", detail = "Cardiac Output (Qp in absence of shunt)")
                ),
                outputs = outputs,
                notes = emptyList()
            )
        )
    }

    // ---------------------------------------------------------------------
    // TD dentro de Fick (se guarda como CalcType.FICK, CO_METHOD="TD")
    // ---------------------------------------------------------------------

    fun upsertThermodilutionInFick(
        timestampMillis: Long,
        title: String,
        tdRun1_LMin: Double?,
        tdRun2_LMin: Double?,
        tdRun3_LMin: Double?,
        coLMin: Double,
        bsaM2: Double?,
        ciLMinM2: Double?,
        svMlBeat: Double?
    ) {
        val co = coLMin.takeIf { it.isFinite() && it > 0.0 } ?: return
        val bsa = bsaM2.finitePositiveOrNull()
        val ci = ciLMinM2.finitePositiveOrNull()
        val sv = svMlBeat.finitePositiveOrNull()

        ReportStore.upsert(
            CalcEntry(
                type = CalcType.FICK, // se mantiene tu convención actual
                timestampMillis = timestampMillis,
                title = title,
                inputs = listOf(
                    LineItem(label = "TD run 1", value = tdRun1_LMin.finiteOrNull()?.let { Format.d(it, 2) } ?: "", unit = "L/min", detail = ""),
                    LineItem(label = "TD run 2", value = tdRun2_LMin.finiteOrNull()?.let { Format.d(it, 2) } ?: "", unit = "L/min", detail = ""),
                    LineItem(label = "TD run 3", value = tdRun3_LMin.finiteOrNull()?.let { Format.d(it, 2) } ?: "", unit = "L/min", detail = ""),

                    LineItem(key = SharedKeys.CO_LMIN, label = "CO", value = Format.d(co, 2), unit = "L/min", detail = ""),
                    LineItem(key = SharedKeys.BSA_M2, label = "BSA", value = bsa?.let { Format.d(it, 2) } ?: "", unit = "m²", detail = ""),
                    LineItem(key = SharedKeys.CI_LMIN_M2, label = "CI", value = ci?.let { Format.d(it, 2) } ?: "", unit = "L/min/m²", detail = ""),
                    LineItem(key = SharedKeys.CO_METHOD, label = "CO method", value = "TD", unit = null, detail = null)
                ),
                outputs = listOf(
                    LineItem(key = SharedKeys.CO_LMIN, label = "CO", value = Format.d(co, 2), unit = "L/min", detail = ""),
                    LineItem(key = SharedKeys.CI_LMIN_M2, label = "CI", value = ci?.let { Format.d(it, 2) } ?: "", unit = "L/min/m²", detail = ""),
                    LineItem(label = "SV", value = sv?.let { Format.d(it, 0) } ?: "", unit = "mL", detail = "")
                ),
                notes = emptyList()
            )
        )
    }
}
