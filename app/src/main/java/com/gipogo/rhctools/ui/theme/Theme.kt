package com.gipogo.rhctools.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GipogoPrimary,
    onPrimary = GipogoTextLight,

    background = GipogoBackgroundDark,
    onBackground = GipogoTextLight,

    surface = GipogoSurfaceDark,
    onSurface = GipogoTextLight,

    // En Material3, outline suele usarse para bordes/contornos.
    outline = GipogoBorderDark,

    // Útil para textos secundarios: cs.onSurfaceVariant
    onSurfaceVariant = GipogoTextMuted
)

private val LightColorScheme = lightColorScheme(
    primary = GipogoPrimary
)

@Composable
fun GipogoRhctoolsTheme(
    darkTheme: Boolean = true,      // por defecto: oscuro como tu diseño
    dynamicColor: Boolean = false,  // por defecto: OFF para respetar tu paleta
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
                dynamicDarkColorScheme(context)

            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
                dynamicLightColorScheme(context)

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
