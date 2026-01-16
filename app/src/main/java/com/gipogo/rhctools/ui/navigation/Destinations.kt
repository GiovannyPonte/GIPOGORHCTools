package com.gipogo.rhctools.ui.navigation

sealed class Destinations(val route: String, val label: String) {
    data object Home : Destinations("home", "Inicio")

    // Graph padre de calculadoras
    data object CalcGraph : Destinations("calc_graph", "Calculos")

    // Rutas internas del graph
    data object Fick : Destinations("fick", "Fick")
    data object Resistances : Destinations("resistances", "Resistencias")
    data object Cpo : Destinations("cpo", "CPO")
    data object Papi : Destinations("papi", "PAPi")

    // ✅ NUEVO
    data object Pvr : Destinations("pvr", "PVR")

    data object PdfPreview : Destinations("pdf_preview", "PDF")
}
