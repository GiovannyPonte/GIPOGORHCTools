package com.gipogo.rhctools.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

object ReportStore {

    // Keep 1 latest calculation per CalcType
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

    // ------------------------------
    // Autofill helpers (by stable key)
    // ------------------------------

    fun latestItemByKey(key: String): LineItem? {
        var bestTime = Long.MIN_VALUE
        var best: LineItem? = null

        for (entry in _entries.value.values) {
            val candidate = (entry.inputs.asSequence() + entry.outputs.asSequence())
                .firstOrNull { it.key == key }
            if (candidate != null && entry.timestampMillis > bestTime) {
                bestTime = entry.timestampMillis
                best = candidate
            }
        }
        return best
    }

    fun latestValueStringByKey(key: String): String? =
        latestItemByKey(key)?.value

    fun latestValueDoubleByKey(key: String): Double? =
        latestItemByKey(key)?.value?.toDoubleOrNull()
}
