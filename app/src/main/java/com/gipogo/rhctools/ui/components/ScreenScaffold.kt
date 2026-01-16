package com.gipogo.rhctools.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBackToMenu: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        // Esto hace que Scaffold considere status bar / cutouts / barras del sistema
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBackToMenu) { Text("←") }
                },
                actions = {
                    TextButton(onClick = onBackToMenu) { Text("Menú") }
                }
            )
        }
    ) { innerPadding ->
        // Asegura que el padding se aplique SIEMPRE al contenedor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(innerPadding)
        }
    }
}
