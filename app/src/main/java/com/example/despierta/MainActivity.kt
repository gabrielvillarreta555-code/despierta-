package com.example.despierta

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var alarms by remember { mutableStateOf(store.getAll()) }
        var draft by remember { mutableStateOf<AlarmModel?>(null) }

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

        DespiertaTheme {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(DespiertaMint, DespiertaSky)
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.NightsStay,
                                    contentDescription = null,
                                    tint = DespiertaInk
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Despierta",
                                    color = DespiertaInk,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    color = Color.White.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        "VERSIÓN 2.0",
                                        color = DespiertaInk,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Text(
                                nextAlarmText(alarms),
                                color = DespiertaInk.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { draft = store.newAlarm() },
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(Icons.Rounded.Add, "Nueva alarma")
                    }
                }
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item {
                        PermissionCards(
                            resumeSignal = resumeSignal,
                            requestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                    if (alarms.isEmpty()) item { EmptyState { draft = store.newAlarm() } }
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onClick = { draft = alarm },
                            onToggle = { enabled ->
                                val updated = alarm.copy(
                                    enabled = enabled,
                                    snoozedUntilMillis = if (enabled) alarm.snoozedUntilMillis else null
                                )
                                store.save(updated)
                                scheduleAndReport(updated)
                                alarms = store.getAll()
                            },
                            onDelete = {
                                scheduler.cancel(alarm.id)
                                store.delete(alarm.id)
                                alarms = store.getAll()
                            }
                        )
                    }
                }
            }

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

    @Composable
    private fun PermissionCards(resumeSignal: Int, requestNotifications: () -> Unit) {
        // Reading the signal makes this section refresh after returning from Settings.
        @Suppress("UNUSED_VARIABLE") val refresh = resumeSignal
        val needsExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !AlarmScheduler(this).canScheduleExactAlarms()
        val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val needsFullScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (needsExact) {
                PermissionCard(
                    title = "Permite alarmas exactas",
                    detail = "Android debe autorizar que suenen puntualmente.",
                    onClick = {
                        openSettings(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    }
                )
            }
            if (needsNotifications) {
                PermissionCard(
                    title = "Activa las notificaciones",
                    detail = "Necesarias para ver y controlar la alarma.",
                    onClick = requestNotifications
                )
            }
            if (needsFullScreen) {
                PermissionCard(
                    title = "Permite pantalla completa",
                    detail = "Muestra la alarma sobre la pantalla bloqueada.",
                    onClick = {
                        openSettings(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    }
                )
            }
        }
    }

    @Composable
    private fun PermissionCard(title: String, detail: String, onClick: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(detail, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }

    @Composable
    private fun AlarmCard(
        alarm: AlarmModel,
        onClick: () -> Unit,
        onToggle: (Boolean) -> Unit,
        onDelete: () -> Unit
    ) {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (alarm.enabled || alarm.snoozedUntilMillis != null) {
                    DespiertaActiveCard
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                1.dp,
                if (alarm.enabled || alarm.snoozedUntilMillis != null) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%02d:%02d".format(alarm.hour, alarm.minute),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.enabled || alarm.snoozedUntilMillis != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alarm.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (alarm.enabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            if (alarm.enabled) "ACTIVA" else "PAUSADA",
                            color = if (alarm.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val labelText = if (alarm.snoozedUntilMillis != null) {
                        "Pospuesta · " + (if (alarm.shiftPattern != null) "Turno ${alarm.shiftPattern.workDays}x${alarm.shiftPattern.restDays}" else daysLabel(alarm.days))
                    } else {
                        if (alarm.shiftPattern != null) "Turno ${alarm.shiftPattern.workDays}x${alarm.shiftPattern.restDays}" else daysLabel(alarm.days)
                    }
                    Text(
                        labelText,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (alarm.mathChallengeEnabled) {
                        Text("Desafío", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(onAdd: () -> Unit) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.AlarmAdd,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))
            Text("Aún no hay alarmas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Crea una para empezar el día a tu hora", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdd) { Text("Crear alarma") }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    private fun AlarmEditor(
        initial: AlarmModel,
        isNew: Boolean,
        onDismiss: () -> Unit,
        onSave: (AlarmModel) -> Unit
    ) {
        var label by remember(initial.id) { mutableStateOf(initial.label) }
        var days by remember(initial.id) { mutableStateOf(initial.days) }
        var soundUri by remember(initial.id) { mutableStateOf(initial.soundUri) }
        var mathChallenge by remember(initial.id) { mutableStateOf(initial.mathChallengeEnabled) }
        var useShift by remember(initial.id) { mutableStateOf(initial.shiftPattern != null) }
        var workDays by remember(initial.id) { mutableStateOf(initial.shiftPattern?.workDays?.toString() ?: "12") }
        var restDays by remember(initial.id) { mutableStateOf(initial.shiftPattern?.restDays?.toString() ?: "4") }
        var startEpochDay by remember(initial.id) {
            mutableStateOf(initial.shiftPattern?.startEpochDay ?: LocalDate.now().toEpochDay())
        }

        val shiftPatternCandidate = if (useShift) {
            val work = workDays.toIntOrNull()
            val rest = restDays.toIntOrNull()
            if (work != null && rest != null) {
                ShiftPattern(
                    startEpochDay = startEpochDay,
                    workDays = work,
                    restDays = rest,
                    exceptionEpochDays = initial.shiftPattern?.exceptionEpochDays ?: emptySet()
                )
            } else {
                null
            }
        } else {
            null
        }
        val shiftInputValid = !useShift || shiftPatternCandidate?.isValid == true

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
                soundUri = uri.toString()
            }
        }
        val timePickerState = rememberTimePickerState(initial.hour, initial.minute, true)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isNew) "Nueva alarma" else "Editar alarma") },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimeInput(timePickerState)
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usar sistema de turnos", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = useShift, onCheckedChange = { useShift = it })
                    }

                    if (useShift) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = workDays,
                                onValueChange = { workDays = it.filter { char -> char.isDigit() } },
                                label = { Text("Días trabajo") },
                                singleLine = true,
                                isError = useShift && (workDays.toIntOrNull() ?: 0) < 1,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = restDays,
                                onValueChange = { restDays = it.filter { char -> char.isDigit() } },
                                label = { Text("Días descanso") },
                                singleLine = true,
                                isError = useShift && (restDays.toIntOrNull() ?: 0) < 1,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!shiftInputValid) {
                            Text(
                                "Indica al menos un día de trabajo y uno de descanso (máximo 366 por ciclo).",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                // Since we can't implement a full DatePicker without significantly expanding the imports
                                // and adding complex Material3 components, we reset the cycle start date to "today".
                                // This provides a functional, albeit simple, way to sync the start of the shift cycle.
                                startEpochDay = LocalDate.now().toEpochDay()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sincronizar inicio de ciclo hoy")
                        }
                    } else {
                        Text("Repetir", fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            listOf("D", "L", "M", "X", "J", "V", "S").forEachIndexed { index, text ->
                                val day = index + 1
                                FilterChip(
                                    selected = day in days,
                                    onClick = { days = if (day in days) days - day else days + day },
                                    label = { Text(text) }
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Desafío matemático", fontWeight = FontWeight.SemiBold)
                            Text("Obligatorio para detener", fontSize = 12.sp)
                        }
                        Switch(checked = mathChallenge, onCheckedChange = { mathChallenge = it })
                    }
                    OutlinedButton(
                        onClick = {
                            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir sonido")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pattern = shiftPatternCandidate
                        if (useShift && pattern == null) return@Button

                        onSave(
                            initial.copy(
                                hour = timePickerState.hour,
                                minute = timePickerState.minute,
                                label = label.ifBlank { "Alarma" },
                                    days = if (useShift) emptySet() else days,
                                    shiftPattern = pattern,
                                soundUri = soundUri.ifBlank { AlarmStore.defaultAlarmUri() },
                                enabled = true,
                                mathChallengeEnabled = mathChallenge,
                                snoozedUntilMillis = null
                            )
                        )
                    },
                    enabled = shiftInputValid
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
    }

    private fun openSettings(action: String, data: Uri) {
        runCatching { startActivity(Intent(action, data)) }
            .onFailure { AlarmLog.error("settings_open_failed", "action=$action", it) }
    }

    private fun daysLabel(days: Set<Int>) = when {
        days.isEmpty() -> "Una vez"
        days.size == 7 -> "Todos los días"
        days == setOf(2, 3, 4, 5, 6) -> "Entre semana"
        else -> listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
            .filterIndexed { index, _ -> index + 1 in days }
            .joinToString(" · ")
    }

    private fun nextAlarmText(alarms: List<AlarmModel>): String =
        if (alarms.none { it.enabled || it.snoozedUntilMillis != null }) {
            "Sin alarmas activas"
        } else {
            "Tu próximo despertar está programado"
        }
}

@Composable
private fun DespiertaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DespiertaMint,
            onPrimary = DespiertaInk,
            secondary = DespiertaSky,
            background = Color(0xFF061315),
            surface = Color(0xFF102326),
            surfaceVariant = Color(0xFF1A3639),
            onSurface = Color(0xFFF1FFFD),
            onSurfaceVariant = Color(0xFFA7C7C3),
            tertiaryContainer = Color(0xFF3A2B19)
        ),
        content = content
    )
}

private val DespiertaMint = Color(0xFF65E6D8)
private val DespiertaSky = Color(0xFF72B8FF)
private val DespiertaInk = Color(0xFF062326)
private val DespiertaActiveCard = Color(0xFF102D30)
