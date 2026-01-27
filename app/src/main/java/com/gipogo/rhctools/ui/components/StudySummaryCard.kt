package com.gipogo.rhctools.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gipogo.rhctools.ui.reports.RhcVisualReports

@Composable
fun StudySummaryCard(modifier: Modifier = Modifier) {
    val groups = RhcVisualReports.collectAvailableGroups()
    groups.forEach { g ->
        RhcVisualReports.SummaryGroupCard(group = g, modifier = modifier)
    }
}
