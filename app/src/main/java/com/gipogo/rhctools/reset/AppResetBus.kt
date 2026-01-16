package com.gipogo.rhctools.reset

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppResetBus {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    fun resetAll() {
        _tick.value = _tick.value + 1
    }
}
