package com.gipogo.rhctools.ui.navigation

object CalcFlow {

    val orderedRoutes = listOf(
        Destinations.Fick.route,
        Destinations.Cpo.route,
        Destinations.Resistances.route,
        Destinations.Pvr.route,
        Destinations.Papi.route
    )

    fun next(current: String?): String {
        val i = orderedRoutes.indexOf(current)
        return if (i == -1) orderedRoutes.first()
        else orderedRoutes[(i + 1) % orderedRoutes.size]
    }

    fun prev(current: String?): String {
        val i = orderedRoutes.indexOf(current)
        return if (i == -1) orderedRoutes.last()
        else orderedRoutes[(i - 1 + orderedRoutes.size) % orderedRoutes.size]
    }
}
