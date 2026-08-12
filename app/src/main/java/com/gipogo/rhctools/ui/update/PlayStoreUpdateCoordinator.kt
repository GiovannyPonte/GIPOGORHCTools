package com.gipogo.rhctools.ui.update

import android.content.IntentSender.SendIntentException
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gipogo.rhctools.R
import com.gipogo.rhctools.data.AppPreferences
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class PlayStoreUpdateCoordinator(
    private val activity: AppCompatActivity,
    private val preferences: AppPreferences,
    private val coroutineScope: CoroutineScope,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity.applicationContext)

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> showDownloadedPrompt()
            InstallStatus.INSTALLED -> dismissDownloadedPrompt()
        }
    }

    private var downloadedDialog: AlertDialog? = null
    private var pendingDailyCheck: Job? = null

    fun start() {
        appUpdateManager.registerListener(installStateListener)
    }

    fun stop() {
        pendingDailyCheck?.cancel()
        dismissDownloadedPrompt()
        appUpdateManager.unregisterListener(installStateListener)
    }

    fun checkForUpdatesIfDue() {
        if (pendingDailyCheck?.isActive == true) return

        pendingDailyCheck = coroutineScope.launch {
            val todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val lastCheckEpochDay = preferences.playStoreUpdateLastCheckEpochDay.first()
            if (!AppUpdatePolicy.isDailyCheckDue(lastCheckEpochDay, todayEpochDay)) return@launch
            requestAvailableUpdate(showResult = false) {
                coroutineScope.launch {
                    preferences.setPlayStoreUpdateLastCheckEpochDay(todayEpochDay)
                }
            }
        }
    }

    fun checkForUpdatesNow(showResult: Boolean = true) {
        requestAvailableUpdate(showResult = showResult)
    }

    fun onResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> showDownloadedPrompt()
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                        startUpdateFlow(info, AppUpdateType.IMMEDIATE)
                }
            }
            .addOnFailureListener(activity) { error ->
                Log.w(TAG, "Unable to inspect Play Store update state on resume.", error)
            }
    }

    private fun requestAvailableUpdate(
        showResult: Boolean,
        onSuccessfulResponse: () -> Unit = {},
    ) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { info ->
                onSuccessfulResponse()
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    showDownloadedPrompt()
                    return@addOnSuccessListener
                }

                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                    if (showResult) toast(R.string.app_update_up_to_date)
                    return@addOnSuccessListener
                }

                val updateType = selectUpdateType(info)
                if (updateType == null) {
                    if (showResult) toast(R.string.app_update_not_allowed)
                    return@addOnSuccessListener
                }
                startUpdateFlow(info, updateType)
            }
            .addOnFailureListener(activity) { error ->
                Log.w(TAG, "Play Store update check failed.", error)
                if (showResult) toast(R.string.app_update_check_failed)
            }
    }

    private fun selectUpdateType(info: AppUpdateInfo): Int? = AppUpdatePolicy.selectMode(
        updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
        flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
        priority = info.updatePriority(),
    )?.let { mode ->
        when (mode) {
            AppUpdateMode.FLEXIBLE -> AppUpdateType.FLEXIBLE
            AppUpdateMode.IMMEDIATE -> AppUpdateType.IMMEDIATE
        }
    }

    private fun startUpdateFlow(
        info: AppUpdateInfo,
        @AppUpdateType updateType: Int,
    ) {
        runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(updateType)
                    .setAllowAssetPackDeletion(false)
                    .build()
            )
        }.onFailure { error ->
            when (error) {
                is SendIntentException -> Log.w(TAG, "Unable to launch Play Store update flow.", error)
                else -> Log.w(TAG, "Unexpected Play Store update error.", error)
            }
        }
    }

    private fun showDownloadedPrompt() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (downloadedDialog?.isShowing == true) return

        dismissDownloadedPrompt()

        downloadedDialog = AlertDialog.Builder(activity)
            .setTitle(text(R.string.app_update_downloaded_title))
            .setMessage(text(R.string.app_update_downloaded_body))
            .setPositiveButton(text(R.string.app_update_downloaded_confirm)) { _, _ ->
                appUpdateManager.completeUpdate()
            }
            .setNegativeButton(text(R.string.app_update_downloaded_later), null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { downloadedDialog = null }
                dialog.show()
            }
    }

    private fun dismissDownloadedPrompt() {
        downloadedDialog?.dismiss()
        downloadedDialog = null
    }

    private fun text(@StringRes resId: Int): String = activity.getString(resId)

    private fun toast(@StringRes resId: Int) {
        Toast.makeText(activity, text(resId), Toast.LENGTH_LONG).show()
    }

    private companion object {
        private const val TAG = "PlayStoreUpdate"
    }
}
