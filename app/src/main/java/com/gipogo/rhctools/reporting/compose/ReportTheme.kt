package com.gipogo.rhctools.reporting.compose

import androidx.compose.ui.graphics.Color

object ReportColors {
    val PageBg = Color(0xFF121826)
    val HeaderBg = Color(0xFF0F172A)
    val HeaderText = Color(0xFFE5E7EB)
    val HeaderSub = Color(0xFF9CA3AF)

    val CardBg = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE5E7EB)

    val Label = Color(0xFF6B7280)
    val Value = Color(0xFF111827)
}

object ReportLayout {
    // A4 aspect ratio (portrait): 210/297 ≈ 0.707
    const val A4_ASPECT = 210f / 297f
}
