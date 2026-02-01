package com.gipogo.rhctools.data.db

import android.content.Context

/**
 * DbProvider
 *
 * Fuente única de verdad para obtener la instancia de Room.
 *
 * B.4-2 (integridad/t trazabilidad):
 * - Evita crear múltiples DBs con nombres distintos.
 * - Delegamos a AppDatabase.getInstance(), que define el nombre correcto y la estrategia de migración.
 *
 * Nota: Mantener un único builder reduce riesgo de “escribí en una DB y leí de otra”.
 */
object DbProvider {

    fun get(context: Context): AppDatabase {
        // Delegación directa a AppDatabase para garantizar:
        // - Un solo nombre de archivo DB
        // - Un solo builder
        // - Una sola política de migración
        return AppDatabase.getInstance(context)
    }
}
