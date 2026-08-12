package com.gipogo.rhctools

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gipogo.rhctools.data.AppPreferences
import com.gipogo.rhctools.ui.AppEntry
import com.gipogo.rhctools.ui.security.AuthSessionManager
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme
import com.gipogo.rhctools.ui.update.PlayStoreUpdateCoordinator
import com.gipogo.rhctools.ui.update.AppUpdateHost
import com.gipogo.rhctools.workshop.WorkshopMode
import com.gipogo.rhctools.workshop.WorkshopSession
import com.gipogo.rhctools.workshop.persistence.WorkshopRhcAutosave
import kotlinx.coroutines.launch

/**
 * Android entry point and owner of activity-scoped integrations.
 *
 * UI rendering is delegated to Compose. This activity only coordinates lifecycle-sensitive work:
 * authentication setup, Play Store updates, and flushing an active study before backgrounding.
 */
class MainActivity : AppCompatActivity(), AppUpdateHost {
    private val appUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private lateinit var playStoreUpdateCoordinator: PlayStoreUpdateCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(this)
        playStoreUpdateCoordinator = PlayStoreUpdateCoordinator(
            activity = this,
            preferences = AppPreferences(applicationContext),
            coroutineScope = lifecycleScope,
            updateLauncher = appUpdateLauncher
        )
        playStoreUpdateCoordinator.start()

        setContent {
            GipogoRhctoolsTheme {
                AppEntry(
                    onAppReady = playStoreUpdateCoordinator::checkForUpdatesIfDue
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playStoreUpdateCoordinator.onResume()
    }

    override fun checkForAppUpdateManually() {
        playStoreUpdateCoordinator.checkForUpdatesNow(showResult = true)
    }

    override fun onStop() {
        if (WorkshopSession.context.value.mode == WorkshopMode.PATIENT_STUDY) {
            lifecycleScope.launch {
                WorkshopRhcAutosave.flushNowAndWait(applicationContext)
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        playStoreUpdateCoordinator.stop()
        super.onDestroy()
    }
}
