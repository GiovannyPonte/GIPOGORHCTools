package com.gipogo.rhctools.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

object ReportStore {

    // Guardamos 1 “último cálculo” por tipo
    private val _entries = MutableStateFlow<Map<CalcType, CalcEntry>>(emptyMap())
    val entries: StateFlow<Map<CalcType, CalcEntry>> = _entries

    val hasAnyResults = entries.map { it.isNotEmpty() }

    fun upsert(entry: CalcEntry) {
        _entries.value = _entries.value + (entry.type to entry)
    }

    fun clear() {
        _entries.value = emptyMap()
    }

    fun snapshot(): List<CalcEntry> =
        _entries.value.values.sortedBy { it.timestampMillis }
}

