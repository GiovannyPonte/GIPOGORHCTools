package com.gipogo.rhctools.report

data class CalcEntry(
    val type: CalcType,
    val timestampMillis: Long,
    val title: String,
    val inputs: List<LineItem>,
    val outputs: List<LineItem>,
    val notes: List<String> = emptyList()
)

enum class CalcType { FICK, SVR, CPO, PAPI }

data class LineItem(
    val label: String,     // "MAP"
    val value: String,     // "87"
    val unit: String? = null,   // "mmHg"
    val detail: String? = null  // "Mean Arterial Pressure"
)
