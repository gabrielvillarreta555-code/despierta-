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

        val permissionState = PermissionUiState(
            needsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !scheduler.canScheduleExactAlarms(),
            needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED,
            needsFullScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        )

        DespiertaTheme {
            DespiertaApp(
                alarms = alarms,
                section = section,
                permissionState = permissionState,
                hapticsEnabled = hapticsEnabled,
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
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestFullScreen = {
                    openSettings(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                }
            )

            draft?.let { target ->
                AlarmEditor(
                    initial = target,
                    isNew = alarms.none { it.id == target.id },
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
}
