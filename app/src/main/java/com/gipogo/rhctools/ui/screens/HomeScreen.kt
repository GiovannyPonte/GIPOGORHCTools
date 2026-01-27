package com.gipogo.rhctools.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gipogo.rhctools.R
import com.gipogo.rhctools.ui.components.GipogoTopBar
import com.gipogo.rhctools.ui.security.AuthSessionManager
import com.gipogo.rhctools.ui.security.BiometricGate
import kotlinx.coroutines.launch

private const val TAG_HOME_AUTH = "HOME_AUTH"

private val CardShape = RoundedCornerShape(28.dp)
private val IconTileShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(50)

data class HomeMedicalTokens(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

enum class HomeBottomTab { Home, History, Settings }

/**
 * Resumen “permitido” SOLO cuando la sesión ya está autenticada.
 * - Si NO hay sesión: el Home NO muestra estos datos.
 */
data class LastPatientUi(
    val patientId: String,
    val displayName: String,
    val ageYears: Int?,
    val lastStudyLabel: String?
)

@Composable
private fun rememberHomeTokens(): HomeMedicalTokens {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (!dark) {
            HomeMedicalTokens(
                primary = Color(0xFF215FA6),
                background = Color(0xFFF7F9FF),
                surface = Color(0xFFFDFBFF),
                surfaceVariant = Color(0xFFE0E2EC),
                onSurfaceVariant = Color(0xFF44474E),
                primaryContainer = Color(0xFFD6E3FF),
                onPrimaryContainer = Color(0xFF001B3E),
                secondaryContainer = Color(0xFFDBE3F1),
                onSecondaryContainer = Color(0xFF1A1C1E),
            )
        } else {
            HomeMedicalTokens(
                primary = Color(0xFF215FA6),
                background = Color(0xFF1A1C1E),
                surface = Color(0xFF111318),
                surfaceVariant = Color(0xFF44474E),
                onSurfaceVariant = Color(0xFFC4C6D0),
                primaryContainer = Color(0xFF284777),
                onPrimaryContainer = Color(0xFFD6E3FF),
                secondaryContainer = Color(0xFF3E4759),
                onSecondaryContainer = Color(0xFFE6E9EF),
            )
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,

    // Navegación real
    onOpenCalculators: () -> Unit,
    onOpenPatients: () -> Unit,
    onOpenLastPatient: (patientId: String) -> Unit,

    // Datos del último paciente (pueden venir de tu DB/DataStore)
    lastPatient: LastPatientUi?,

    onOpenProfile: (() -> Unit)? = null,

    // UI opcional
    showBottomNav: Boolean = false,
    selectedBottomTab: HomeBottomTab = HomeBottomTab.Home,
    onTabHome: (() -> Unit)? = null,
    onTabHistory: (() -> Unit)? = null,
    onTabSettings: (() -> Unit)? = null,

    // Seguridad
    requireAuthForPatients: Boolean = true,
    requireAuthForLastPatient: Boolean = true,
    authSessionTimeoutMs: Long = 3 * 60 * 1000L,
) {
    val t = rememberHomeTokens()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // “Sesión” simple: timestamp del último éxito

    val context = LocalContext.current

    fun hasValidSession(now: Long = System.currentTimeMillis()): Boolean {
        return AuthSessionManager.isSessionValid(now)
    }


    fun showSnack(resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    fun requestAuth(titleRes: Int, subtitleRes: Int, onSuccess: () -> Unit) {
        Log.e(TAG_HOME_AUTH, "requestAuth() -> start")

        // ✅ NO cachear activity fuera: resolver aquí siempre
        val (act, trace) = context.findFragmentActivityWithTrace()

        Log.e(TAG_HOME_AUTH, "LocalContext=${context::class.java.name}")
        Log.e(TAG_HOME_AUTH, "ContextWrapper chain:\n$trace")
        Log.e(TAG_HOME_AUTH, "FragmentActivity found=${act?.javaClass?.name}")

        if (act == null) {
            Log.e(TAG_HOME_AUTH, "activity == null (cannot show BiometricPrompt)")
            showSnack(R.string.auth_unavailable_unknown)
            return
        }

        Log.e(TAG_HOME_AUTH, "calling BiometricGate.authenticate()")
        BiometricGate.authenticate(
            activity = act,
            titleRes = titleRes,
            subtitleRes = subtitleRes,
            onResult = { result ->
                when (result) {
                    is BiometricGate.AuthResult.Success -> {
                        AuthSessionManager.markAuthenticated()
                        onSuccess()
                    }


                    is BiometricGate.AuthResult.Canceled -> {
                        Log.e(TAG_HOME_AUTH, "Auth CANCELED")
                        // Cancelación voluntaria: no hacemos nada
                    }

                    is BiometricGate.AuthResult.NotAvailable -> {
                        Log.e(
                            TAG_HOME_AUTH,
                            "Auth NOT_AVAILABLE reason=${result.reason} msgRes=${result.messageRes}"
                        )
                        showSnack(result.messageRes)
                    }

                    is BiometricGate.AuthResult.Error -> {
                        Log.e(
                            TAG_HOME_AUTH,
                            "Auth ERROR code=${result.code} msgRes=${result.messageRes}"
                        )
                        showSnack(result.messageRes)
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = t.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ✅ Respeta barra de estado del teléfono
            Column(modifier = Modifier.statusBarsPadding()) {
                GipogoTopBar(
                    title = stringResource(R.string.home_topbar_title),
                    subtitle = stringResource(R.string.home_topbar_subtitle),
                    rightGlyph = "👤",
                    onRightClick = { onOpenProfile?.invoke() }
                )
            }
        },
        bottomBar = {
            if (showBottomNav) {
                HomeBottomBar(
                    tokens = t,
                    selected = selectedBottomTab,
                    onTabHome = onTabHome,
                    onTabHistory = onTabHistory,
                    onTabSettings = onTabSettings
                )
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1) PRIMARY: Pacientes (flujo canónico)
            PatientsPrimaryCard(
                tokens = t,
                enabled = true,
                onClick = {
                    Log.e("HOME_777", "Open Patients CLICKED")

                    if (!requireAuthForPatients) {
                        onOpenPatients()
                        return@PatientsPrimaryCard
                    }
                    if (hasValidSession()) {
                        onOpenPatients()
                    } else {
                        requestAuth(
                            titleRes = R.string.auth_prompt_patients_title,
                            subtitleRes = R.string.auth_prompt_patients_subtitle,
                            onSuccess = onOpenPatients
                        )
                    }
                }
            )

            // 2) Secondary: Acceso rápido al último paciente
            LastPatientQuickAccessCard(
                tokens = t,
                lastPatient = lastPatient,
                isSessionUnlocked = hasValidSession(),
                enabled = lastPatient != null,
                onOpenLocked = {
                    if (lastPatient == null) return@LastPatientQuickAccessCard
                    if (!requireAuthForLastPatient) {
                        onOpenLastPatient(lastPatient.patientId)
                        return@LastPatientQuickAccessCard
                    }
                    requestAuth(
                        titleRes = R.string.auth_prompt_last_patient_title,
                        subtitleRes = R.string.auth_prompt_last_patient_subtitle,
                        onSuccess = { onOpenLastPatient(lastPatient.patientId) }
                    )
                },
                onOpenUnlocked = {
                    if (lastPatient == null) return@LastPatientQuickAccessCard
                    onOpenLastPatient(lastPatient.patientId)
                }
            )

            // 3) Tertiary: Calculadoras (sin persistencia)
            QuickCalcsSecondaryCard(
                tokens = t,
                onOpenCalculators = onOpenCalculators
            )

            Spacer(Modifier.height(2.dp))
            PrivacyFooter(tokens = t)
        }
    }
}

/**
 * Card principal (Pacientes)
 */
@Composable
private fun PatientsPrimaryCard(
    tokens: HomeMedicalTokens,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = tokens.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = IconTileShape,
                    color = tokens.primary,
                    contentColor = Color.White
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderShared,
                            contentDescription = null
                        )
                    }
                }

                Spacer(Modifier.size(12.dp))

                Text(
                    text = stringResource(R.string.home_landing_patients_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.onPrimaryContainer
                )
            }

            Text(
                text = stringResource(R.string.home_landing_patients_body),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.onPrimaryContainer.copy(alpha = 0.86f),
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = Color.White,
                    disabledContainerColor = tokens.primary.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.8f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_landing_patients_cta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Card secundaria: “Último paciente”
 */
@Composable
private fun LastPatientQuickAccessCard(
    tokens: HomeMedicalTokens,
    lastPatient: LastPatientUi?,
    isSessionUnlocked: Boolean,
    enabled: Boolean,
    onOpenLocked: () -> Unit,
    onOpenUnlocked: () -> Unit
) {
    val showUnlockedDetails = isSessionUnlocked && lastPatient != null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f),
        shape = CardShape,
        color = tokens.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, tokens.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = IconTileShape,
                    color = Color.White.copy(alpha = 0.16f),
                    contentColor = tokens.primary
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = tokens.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.size(10.dp))

                Text(
                    text = stringResource(R.string.home_last_patient_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (showUnlockedDetails)
                        stringResource(R.string.home_last_patient_status_unlocked)
                    else
                        stringResource(R.string.home_last_patient_status_locked),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }

            if (!enabled) {
                Text(
                    text = stringResource(R.string.home_last_patient_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.onSurfaceVariant.copy(alpha = 0.75f)
                )
            } else if (!showUnlockedDetails) {
                Text(
                    text = stringResource(R.string.home_last_patient_body_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.onSurfaceVariant.copy(alpha = 0.75f)
                )

                OutlinedButton(
                    onClick = onOpenLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = PillShape,
                    border = BorderStroke(0.dp, Color.Transparent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = tokens.surface,
                        contentColor = tokens.onSurfaceVariant
                    ),
                    enabled = enabled
                ) {
                    Text(
                        text = stringResource(R.string.home_last_patient_cta_locked),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            } else {
                val name = lastPatient!!.displayName
                val age = lastPatient.ageYears?.let { stringResource(R.string.home_last_patient_age_years, it) }
                    ?: stringResource(R.string.home_last_patient_age_na)
                val study = lastPatient.lastStudyLabel ?: stringResource(R.string.home_last_patient_study_na)

                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.home_last_patient_details_line, age, study),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = onOpenUnlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = Color.White
                    ),
                    enabled = enabled
                ) {
                    Text(
                        text = stringResource(R.string.home_last_patient_cta_unlocked),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

/**
 * Card terciaria: Calculadoras
 */
@Composable
private fun QuickCalcsSecondaryCard(
    tokens: HomeMedicalTokens,
    onOpenCalculators: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = tokens.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = IconTileShape,
                    color = Color.White.copy(alpha = 0.20f),
                    contentColor = tokens.primary
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Calculate,
                            contentDescription = null,
                            tint = tokens.primary
                        )
                    }
                }

                Spacer(Modifier.size(12.dp))

                Column {
                    Text(
                        text = stringResource(R.string.home_landing_quick_calcs_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_landing_quick_calcs_subtitle),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onSecondaryContainer.copy(alpha = 0.70f)
                    )
                }
            }

            Text(
                text = stringResource(R.string.home_landing_quick_calcs_body),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.onSecondaryContainer.copy(alpha = 0.78f),
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            OutlinedButton(
                onClick = onOpenCalculators,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = PillShape,
                border = BorderStroke(0.dp, Color.Transparent),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = tokens.surfaceVariant,
                    contentColor = tokens.onSurfaceVariant
                )
            ) {
                Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.home_landing_quick_calcs_cta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun PrivacyFooter(tokens: HomeMedicalTokens) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = tokens.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.home_landing_privacy_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.home_landing_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.onSurfaceVariant.copy(alpha = 0.70f),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun HomeBottomBar(
    tokens: HomeMedicalTokens,
    selected: HomeBottomTab,
    onTabHome: (() -> Unit)?,
    onTabHistory: (() -> Unit)?,
    onTabSettings: (() -> Unit)?,
) {
    NavigationBar(
        containerColor = tokens.surface,
        tonalElevation = 0.dp
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = tokens.primary,
            selectedTextColor = tokens.primary,
            unselectedIconColor = tokens.onSurfaceVariant.copy(alpha = 0.55f),
            unselectedTextColor = tokens.onSurfaceVariant.copy(alpha = 0.55f),
            indicatorColor = Color.Transparent
        )

        NavigationBarItem(
            selected = selected == HomeBottomTab.Home,
            onClick = { onTabHome?.invoke() },
            enabled = onTabHome != null,
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = colors
        )

        NavigationBarItem(
            selected = selected == HomeBottomTab.History,
            onClick = { onTabHistory?.invoke() },
            enabled = onTabHistory != null,
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_history)) },
            colors = colors
        )

        NavigationBarItem(
            selected = selected == HomeBottomTab.Settings,
            onClick = { onTabSettings?.invoke() },
            enabled = onTabSettings != null,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = colors
        )
    }
}

/** Devuelve FragmentActivity si existe; útil para BiometricPrompt. */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Igual que findFragmentActivity(), pero con traza para logs. */
private fun Context.findFragmentActivityWithTrace(): Pair<FragmentActivity?, String> {
    val sb = StringBuilder()
    var ctx: Context? = this
    var depth = 0
    while (ctx is ContextWrapper) {
        sb.append("[").append(depth).append("] ").append(ctx::class.java.name).append("\n")
        if (ctx is FragmentActivity) return (ctx to sb.toString())
        ctx = ctx.baseContext
        depth++
        if (depth > 25) break
    }
    if (ctx != null) {
        sb.append("[").append(depth).append("] ").append(ctx::class.java.name).append("\n")
        if (ctx is FragmentActivity) return (ctx to sb.toString())
    }
    return (null to sb.toString())
}
