package com.gipogo.rhctools.ui.security

import android.os.SystemClock

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gipogo.rhctools.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedRouteGate(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    @StringRes promptTitleRes: Int = R.string.auth_prompt_patients_title,
    @StringRes promptSubtitleRes: Int = R.string.auth_prompt_patients_subtitle,
    @StringRes lockedTitleRes: Int = R.string.auth_locked_patients_title,
    @StringRes lockedBodyRes: Int = R.string.auth_locked_patients_body,
    @StringRes lockedActionRes: Int = R.string.auth_locked_patients_cta,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = LocalResources.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAuthed by remember { mutableStateOf(AuthSessionManager.isSessionValid()) }
    var authInFlight by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    fun showSnack(@StringRes resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(resId)) }
    }

    fun requestAuth() {
        if (authInFlight) return
        if (AuthSessionManager.isSessionValid()) {
            isAuthed = true
            return
        }

        if (activity == null) {
            showSnack(R.string.auth_unavailable_unknown)
            return
        }

        authInFlight = true
        BiometricGate.authenticate(
            activity = activity,
            titleRes = promptTitleRes,
            subtitleRes = promptSubtitleRes,
            onResult = { result ->
                authInFlight = false
                when (result) {
                    is BiometricGate.AuthResult.Success -> {
                        AuthSessionManager.markAuthenticated()
                        isAuthed = true
                    }

                    is BiometricGate.AuthResult.Canceled -> Unit

                    is BiometricGate.AuthResult.NotAvailable -> {
                        isAuthed = false
                        showSnack(result.messageRes)
                    }

                    is BiometricGate.AuthResult.Error -> {
                        isAuthed = false
                        showSnack(result.messageRes)
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!isAuthed) requestAuth()
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    LaunchedEffect(nowMillis, isAuthed) {
        val sessionValid = AuthSessionManager.isSessionValid(nowMillis)
        when {
            isAuthed && !sessionValid -> isAuthed = false
            !isAuthed && sessionValid -> isAuthed = true
        }
    }

    if (isAuthed) {
        content()
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(promptTitleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(lockedTitleRes),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(lockedBodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { requestAuth() },
                        enabled = !authInFlight,
                        modifier = Modifier.testTag("protected_route_auth_button")
                    ) {
                        Text(text = stringResource(lockedActionRes))
                    }
                }
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}
