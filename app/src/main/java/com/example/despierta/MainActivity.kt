package com.example.despierta

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var resumeGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmService.ensureNotificationChannel(this)
        setContent { AlarmApp(resumeGeneration) }
    }

    override fun onResume() {
        super.onResume()
        val store = AlarmStore(this)
        AlarmScheduler(this).reconcile(store.getAll())
        resumeGeneration++
    }

    @Composable
    private fun AlarmApp(resumeSignal: Int) {
        val store = remember { AlarmStore(this) }
        val scheduler = remember { AlarmScheduler(this) }
        val uiPreferences = remember { getSharedPreferences("despierta_ui", MODE_PRIVATE) }
        val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val hapticFeedback = LocalHapticFeedback.current
        var alarms by remember { mutableStateOf(store.getAll()) }
        var draft by remember { mutableStateOf<AlarmModel?>(null) }
        var section by rememberSaveable { mutableStateOf(AppSection.ALARMS) }
        var hapticsEnabled by rememberSaveable {
            mutableStateOf(uiPreferences.getBoolean("haptics_enabled", true))
        }

        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            resumeGeneration++
        }

        LaunchedEffect(resumeSignal) {
            alarms = store.getAll()
        }
        LaunchedEffect(Unit) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun feedback() {
            if (hapticsEnabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        fun scheduleAndReport(alarm: AlarmModel) {
            when (scheduler.schedule(alarm)) {
                ScheduleResult.INEXACT_PERMISSION_REQUIRED -> scope.launch {
                    snackbar.showSnackbar("Activa las alarmas exactas para garantizar la hora.")
                }

                ScheduleResult.FAILED -> scope.launch {
                    snackbar.showSnackbar("No se pudo registrar la alarma. Revisa los permisos.")
                }

                else -> Unit
            }
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        val permissionState = PermissionUiState(
            needsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !scheduler.canScheduleExactAlarms(),
            needsNotifications = !notificationManager.areNotificationsEnabled() ||
                (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ),
            needsFullScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !notificationManager.canUseFullScreenIntent(),
            needsAlarmChannel = AlarmService.isNotificationChannelDegraded(this)
        )

        DespiertaTheme {
            DespiertaApp(
                alarms = alarms,
                section = section,
                permissionState = permissionState,
                hapticsEnabled = hapticsEnabled,
                liveClockUpdates = !(
                    BuildConfig.DEBUG &&
                        intent.getBooleanExtra(EXTRA_DISABLE_LIVE_CLOCK_FOR_TESTS, false)
                    ),
                snackbarHostState = snackbar,
                onSectionSelected = {
                    feedback()
                    section = it
                },
                onAddAlarm = {
                    feedback()
                    draft = store.newAlarm()
                },
                onAddShiftAlarm = {
                    feedback()
                    draft = store.newAlarm().copy(
                        label = "Mi turno",
                        shiftPattern = ShiftPattern.from(LocalDate.now(), 12, 4)
                    )
                },
                onEditAlarm = {
                    feedback()
                    draft = it
                },
                onToggleAlarm = { alarm, enabled ->
                    feedback()
                    val updated = alarm.copy(
                        enabled = enabled,
                        snoozedUntilMillis = if (enabled) alarm.snoozedUntilMillis else null
                    )
                    store.save(updated)
                    scheduleAndReport(updated)
                    alarms = store.getAll()
                },
                onDeleteAlarm = { alarm ->
                    feedback()
                    scheduler.cancel(alarm.id)
                    store.delete(alarm.id)
                    alarms = store.getAll()
                },
                onHapticsChanged = { enabled ->
                    hapticsEnabled = enabled
                    uiPreferences.edit().putBoolean("haptics_enabled", enabled).apply()
                    if (enabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onRequestExactAlarm = {
                    openSettings(
                        ACTION_REQUEST_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                },
                onRequestNotifications = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            )
                        }.onFailure {
                            AlarmLog.error("notification_settings_open_failed", "", it)
                        }
                    }
                },
                onRequestFullScreen = {
                    openSettings(
                        ACTION_MANAGE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                },
                onRequestAlarmChannel = {
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID)
                        )
                    }.onFailure {
                        AlarmLog.error("channel_settings_open_failed", "channel=${AlarmService.CHANNEL_ID}", it)
                    }
                }
            )

            draft?.let { target ->
                AlarmEditor(
                    initial = target,
                    isNew = alarms.none { it.id == target.id },
                    hapticsEnabled = hapticsEnabled,
                    onDismiss = { draft = null },
                    onSave = { saved ->
                        store.save(saved)
                        scheduleAndReport(saved)
                        alarms = store.getAll()
                        draft = null
                    }
                )
            }
        }
    }

    private fun openSettings(action: String, data: Uri) {
        runCatching { startActivity(Intent(action, data)) }
            .onFailure { AlarmLog.error("settings_open_failed", "action=$action", it) }
    }

    companion object {
        internal const val EXTRA_DISABLE_LIVE_CLOCK_FOR_TESTS =
            "com.example.despierta.extra.DISABLE_LIVE_CLOCK_FOR_TESTS"
        // Inlined strings keep the guarded settings actions safe on the minSdk 26 code path.
        private const val ACTION_REQUEST_EXACT_ALARM =
            "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
        private const val ACTION_MANAGE_FULL_SCREEN_INTENT =
            "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
    }
}
