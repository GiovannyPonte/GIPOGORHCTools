package com.gipogo.rhctools.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.gipogo.rhctools.report.PdfReportGenerator
import com.gipogo.rhctools.report.PdfSession
import com.gipogo.rhctools.report.ReportStore
import com.gipogo.rhctools.ui.navigation.Destinations
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {

    val hasAny by ReportStore.hasAnyResults.collectAsState(initial = false)
    val context = androidx.compose.ui.platform.LocalContext.current

    var showPrepDialog by remember { mutableStateOf(false) }

    // Header colors
    val surface = MaterialTheme.colorScheme.surface
    val containerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "GIPOGO RHC Tools",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Quitamos el texto largo que no te convence (pedido del usuario)
                        Text(
                            "Hemodinámica rápida (RHC)",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // --- Preparación rápida (compacta + modal) ---
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = containerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Preparación rápida",
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            "• Presiones con Swan-Ganz + CO\n• Si harás Fick: arterial + PA (SvO₂) y Hb",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )

                        TextButton(onClick = { showPrepDialog = true }) {
                            Text("Ver más")
                        }
                    }
                }
            }

            // --- Sección: Cálculos ---
            item {
                Text(
                    "Cálculos",
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceVariant
                )
            }

            item {
                ToolRow(
                    badge = "CO",
                    title = "Fick: gasto cardíaco",
                    subtitle = "CO/CI/SV con peso, talla, SaO₂, SvO₂ (PA), Hb, HR y edad.",
                    onClick = { navController.navigate(Destinations.Fick.route) }
                )
            }

            item {
                ToolRow(
                    badge = "SVR",
                    title = "Resistencia vascular sistémica",
                    subtitle = "MAP + CVP + CO. Salida en Wood Units o dyn·s·cm⁻⁵.",
                    onClick = { navController.navigate(Destinations.Resistances.route) }
                )
            }

            item {
                ToolRow(
                    badge = "CPO",
                    title = "Potencia cardíaca",
                    subtitle = "CPO/CPI con MAP + CO (+ BSA opcional).",
                    onClick = { navController.navigate(Destinations.Cpo.route) }
                )
            }

            item {
                ToolRow(
                    badge = "PAPi",
                    title = "Índice de pulsatilidad pulmonar",
                    subtitle = "(PASP − PADP) / RAP.",
                    onClick = { navController.navigate(Destinations.Papi.route) }
                )
            }

            // --- Sección: Reporte ---
            item {
                Text(
                    "Reporte",
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        // Que destaque si hay cálculos
                        containerColor = if (hasAny) MaterialTheme.colorScheme.primaryContainer else containerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Reporte PDF",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            if (hasAny)
                                "Listo para generar un PDF con fecha/hora y los cálculos realizados."
                            else
                                "Realiza al menos un cálculo para habilitar el reporte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = hasAny,
                                onClick = {
                                    // Generar PDF en cache
                                    val file = File(context.cacheDir, "GIPOGO_RHC_Report.pdf")
                                    FileOutputStream(file).use { os ->
                                        PdfReportGenerator.writePdf(
                                            context = context,
                                            outputStream = os,
                                            appName = "GIPOGO Cath Lab Hemodynamics",
                                            entries = ReportStore.snapshot(),
                                            nowMillis = System.currentTimeMillis()
                                        )
                                    }

                                    val uri: Uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )

                                    PdfSession.lastPdfFile = file
                                    PdfSession.lastPdfUri = uri

                                    navController.navigate(Destinations.PdfPreview.route)
                                }
                            ) { Text("Abrir PDF") }

                            OutlinedButton(
                                enabled = hasAny,
                                onClick = {
                                    ReportStore.clear()
                                    com.gipogo.rhctools.reset.AppResetBus.resetAll()
                                }
                            ) { Text("Reset") }
                        }
                    }
                }
            }

            // Espacio final
            item { Spacer(modifier = Modifier.height(6.dp)) }
        }
    }

    // --- Modal preparación completa ---
    if (showPrepDialog) {
        AlertDialog(
            onDismissRequest = { showPrepDialog = false },
            title = { Text("Preparación completa") },
            text = {
                Text(
                    "• Presiones Swan-Ganz: RAP (AD), PA (PASP/PADP/mPAP) y PCWP (wedge) idealmente al final de la espiración.\n\n" +
                            "• Si harás Fick: muestra arterial sistémica y venosa mixta de arteria pulmonar (PA). Etiqueta el sitio de toma y envía a co-oximetría/gasometría para SaO₂/SvO₂ y Hb.\n\n" +
                            "• MAP: preferible línea arterial invasiva; alternativa, TA no invasiva y MAP ≈ (PAS + 2×PAD)/3.\n\n" +
                            "• CO: usa un método consistente (termodilución o Fick) para resistencias y potencia.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrepDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun ToolRow(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val containerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = containerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge (sigla)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Chevron simple (sin icons para no meter dependencias)
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
