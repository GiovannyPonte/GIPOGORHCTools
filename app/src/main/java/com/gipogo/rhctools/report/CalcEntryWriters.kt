package com.gipogo.rhctools.report

/**
 * Helpers para construir y persistir CalcEntry con KEYS canónicas.
 * Regla: esta capa NO calcula clínica; solo arma el “payload” auditable.
 */
object CalcEntryWriters {

    fun upsertCpo(
        timestampMillis: Long,
        title: String,
        mapMmHg: Double?,
        coLMin: Double?,
        bsaM2: Double?,
        cpoW: Double,
        cpiWm2: Double?
    ) {
        val inputs = listOf(
            LineItem(
                key = SharedKeys.MAP_MMHG,
                label = "MAP",
                value = mapMmHg?.let { com.gipogo.rhctools.util.Format.d(it, 0) } ?: "",
                unit = "mmHg",
                detail = "Mean Arterial Pressure"
            ),
            LineItem(
                key = SharedKeys.CO_LMIN,
                label = "CO",
                value = coLMin?.let { com.gipogo.rhctools.util.Format.d(it, 2) } ?: "",
                unit = "L/min",
                detail = "Cardiac Output"
            ),
            LineItem(
                key = SharedKeys.BSA_M2,
                label = "BSA",
                value = bsaM2?.let { com.gipogo.rhctools.util.Format.d(it, 2) } ?: "",
                unit = "m²",
                detail = "Body Surface Area"
            )
        )

        val outputs = listOfNotNull(
            LineItem(
                key = SharedKeys.CPO_W,
                label = "CPO",
                value = com.gipogo.rhctools.util.Format.d(cpoW, 2),
                unit = "W",
                detail = "Cardiac Power Output"
            ),
            cpiWm2?.let {
                LineItem(
                    key = SharedKeys.CPI_W_M2,
                    label = "CPI",
                    value = com.gipogo.rhctools.util.Format.d(it, 2),
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
}
