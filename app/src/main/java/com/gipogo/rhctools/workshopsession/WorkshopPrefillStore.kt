package com.gipogo.rhctools.workshop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WorkshopPrefill(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val sex: String? = null,

    // ✅ DOB completo (epoch millis)
    val birthDateMillis: Long? = null
)

object WorkshopPrefillStore {
    private val _prefill = MutableStateFlow(WorkshopPrefill())
    val prefill: StateFlow<WorkshopPrefill> = _prefill

    fun clear() { _prefill.value = WorkshopPrefill() }
    fun set(value: WorkshopPrefill) { _prefill.value = value }
}
