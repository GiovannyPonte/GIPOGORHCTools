package com.gipogo.rhctools.ui.navigation

sealed class Destinations(val route: String, val label: String) {
    data object Home : Destinations("home", "Inicio")
    data object Fick : Destinations("fick", "Fick")
    data object Resistances : Destinations("resistances", "Resistencias")
    data object Cpo : Destinations("cpo", "CPO")
    data object Papi : Destinations("papi", "PAPi")
    data object PdfPreview : Destinations("pdf_preview", "PDF")

}
