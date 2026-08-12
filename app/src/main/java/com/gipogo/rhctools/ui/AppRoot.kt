package com.gipogo.rhctools.ui

import androidx.compose.runtime.Composable
import com.gipogo.rhctools.ui.navigation.AppNavGraph

/** Root of the accepted-disclaimer UI; navigation owns the screen back stack below this point. */
@Composable
fun AppRoot() {
    AppNavGraph()
}
