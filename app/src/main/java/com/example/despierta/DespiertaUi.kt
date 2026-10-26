package com.example.despierta

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

internal enum class AppSection(val label: String, val icon: ImageVector) {
    ALARMS("Alarmas", Icons.Rounded.Alarm),
    SHIFTS("Turnos", Icons.Rounded.WorkHistory),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

internal data class PermissionUiState(
    val needsExactAlarm: Boolean,
    val needsNotifications: Boolean,
    val needsFullScreen: Boolean,
    val needsAlarmChannel: Boolean
) {
    val pendingCount: Int
        get() = listOf(
            needsExactAlarm,
            needsNotifications,
            needsFullScreen,
            needsAlarmChannel
        ).count { it }
}

@Composable
internal fun DespiertaApp(
    alarms: List<AlarmModel>,
    section: AppSection,
    permissionState: PermissionUiState,
    hapticsEnabled: Boolean,
    liveClockUpdates: Boolean = true,
    snackbarHostState: SnackbarHostState,
    onSectionSelected: (AppSection) -> Unit,
    onAddAlarm: () -> Unit,
    onAddShiftAlarm: () -> Unit,
    onEditAlarm: (AlarmModel) -> Unit,
    onToggleAlarm: (AlarmModel, Boolean) -> Unit,
    onDeleteAlarm: (AlarmModel) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onRequestAlarmChannel: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(liveClockUpdates) {
        if (!liveClockUpdates) {
            nowMillis = System.currentTimeMillis()
            return@DisposableEffect onDispose { }
        }
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                nowMillis = System.currentTimeMillis()
                val nextTick = (1_000L - nowMillis % 1_000L).coerceAtLeast(100L)
                handler.postDelayed(this, nextTick)
            }
        }
        handler.post(ticker)
        onDispose { handler.removeCallbacks(ticker) }
    }

    Box(Modifier.fillMaxSize()) {
        NightLandscapeBackground()
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { BrandBar() },
            bottomBar = {
                AppNavigation(
                    selected = section,
                    permissionCount = permissionState.pendingCount,
                    onSelected = onSectionSelected
                )
            },
            floatingActionButton = {
                if (section != AppSection.SETTINGS) {
                    ExtendedFloatingActionButton(
                        onClick = if (section == AppSection.SHIFTS) onAddShiftAlarm else onAddAlarm,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(if (section == AppSection.SHIFTS) "Nuevo turno" else "Nueva alarma") },
                        modifier = Modifier.testTag("add_alarm")
                    )
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = section,
                animationSpec = tween(180),
                label = "section",
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) { selected ->
                when (selected) {
                    AppSection.ALARMS -> AlarmHome(
                        alarms = alarms,
                        nowMillis = nowMillis,
                        onAddAlarm = onAddAlarm,
                        onEditAlarm = onEditAlarm,
                        onToggleAlarm = onToggleAlarm,
                        onDeleteAlarm = onDeleteAlarm
                    )

                    AppSection.SHIFTS -> ShiftScreen(
                        alarms = alarms,
                        nowMillis = nowMillis,
                        onAddShiftAlarm = onAddShiftAlarm,
                        onEditAlarm = onEditAlarm,
                        onToggleAlarm = onToggleAlarm
                    )

                    AppSection.SETTINGS -> SettingsScreen(
                        permissionState = permissionState,
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChanged = onHapticsChanged,
                        onRequestExactAlarm = onRequestExactAlarm,
                        onRequestNotifications = onRequestNotifications,
                        onRequestFullScreen = onRequestFullScreen,
                        onRequestAlarmChannel = onRequestAlarmChannel
                    )
                }
            }
        }
    }
}

@Composable
private fun NightLandscapeBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF040614),
                    Color(0xFF0D122B),
                    Color(0xFF1B1F3C)
                )
            )
        )

        drawCircle(
            color = Color(0xFFFFECCC),
            radius = width * 0.15f,
            center = Offset(width * 0.8f, height * 0.15f)
        )
        drawCircle(
            color = Color(0x20FFECCC),
            radius = width * 0.25f,
            center = Offset(width * 0.8f, height * 0.15f)
        )

        val distantMountainPath = Path().apply {
            moveTo(0f, height * 0.5f)
            quadraticBezierTo(width * 0.2f, height * 0.45f, width * 0.5f, height * 0.52f)
            quadraticBezierTo(width * 0.8f, height * 0.59f, width, height * 0.48f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = distantMountainPath,
            color = Color(0xFF0F1528)
        )

        val midMountainPath = Path().apply {
            moveTo(0f, height * 0.65f)
            quadraticBezierTo(width * 0.3f, height * 0.55f, width * 0.6f, height * 0.68f)
            quadraticBezierTo(width * 0.85f, height * 0.77f, width, height * 0.62f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = midMountainPath,
            color = Color(0xFF080C19)
        )

        val lakePath = Path().apply {
            moveTo(0f, height * 0.75f)
            quadraticBezierTo(width * 0.4f, height * 0.85f, width, height * 0.72f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = lakePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF13172E),
                    Color(0xFF070912)
                ),
                startY = height * 0.75f,
                endY = height
            )
        )
    }
}

@Composable
private fun BrandBar() {
    Surface(color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(62.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(13.dp),
                color = DespiertaAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.NightsStay,
                        contentDescription = null,
                        tint = DespiertaOnAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    "DESPIERTA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.2.sp
                )
                Text(
                    "Tu tiempo, bajo control",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppNavigation(
    selected: AppSection,
    permissionCount: Int,
    onSelected: (AppSection) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        AppSection.entries.forEach { section ->
            NavigationBarItem(
                selected = selected == section,
                onClick = { onSelected(section) },
                icon = {
                    Box {
                        Icon(section.icon, contentDescription = null)
                        if (section == AppSection.SETTINGS && permissionCount > 0) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                },
                label = { Text(section.label) },
                modifier = Modifier.testTag("nav_${section.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun AlarmHome(
    alarms: List<AlarmModel>,
    nowMillis: Long,
    onAddAlarm: () -> Unit,
    onEditAlarm: (AlarmModel) -> Unit,
    onToggleAlarm: (AlarmModel, Boolean) -> Unit,
    onDeleteAlarm: (AlarmModel) -> Unit
) {
    val next = remember(alarms, nowMillis / 1_000L) { findNextAlarm(alarms, nowMillis) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("alarm_home"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TimeHero(nowMillis = nowMillis, next = next) }
        item { SleepDurationCard(nowMillis = nowMillis, next = next) }
        item {
            SectionHeading(
                title = "Tus alarmas",
                detail = "${alarms.count { it.enabled }} activas · ${alarms.size} en total"
            )
        }
        if (alarms.isEmpty()) {
            item { AlarmEmptyState(onAddAlarm) }
        } else {
            items(alarms, key = AlarmModel::id) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onEditAlarm(alarm) },
                    onToggle = { onToggleAlarm(alarm, it) },
                    onDelete = { onDeleteAlarm(alarm) }
                )
            }
        }
    }
}

@Composable
private fun SleepDurationCard(nowMillis: Long, next: AlarmOccurrence?) {
    val remainingMillis = next?.triggerAtMillis?.minus(nowMillis)
    val sleepWindow = remainingMillis?.let(::formatSleepDuration)
    val guidance = when {
        remainingMillis == null -> "Activa una alarma para calcular tu ventana de descanso."
        remainingMillis < 7 * 60 * 60 * 1_000L ->
            "Descanso corto: prepara el ambiente y reduce distracciones antes de dormir."
        remainingMillis <= 9 * 60 * 60 * 1_000L ->
            "Una ventana dentro de la referencia general de 7 a 9 horas para adultos."
        else -> "Aún tienes tiempo para preparar una rutina tranquila antes de acostarte."
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("sleep_duration_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = DespiertaSky.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = DespiertaSky,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "DESCANSO ESTIMADO",
                    color = DespiertaSky,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Text(
                    sleepWindow ?: "Sin próxima alarma",
                    fontSize = if (sleepWindow == null) 18.sp else 27.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("sleep_duration_value")
                )
                Text(
                    guidance,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TimeHero(nowMillis: Long, next: AlarmOccurrence?) {
    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            formatCivilDate(now.toLocalDate()).uppercase(Locale.ROOT),
            color = DespiertaAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            fontSize = 72.sp,
            lineHeight = 72.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = (-2).sp,
            modifier = Modifier.testTag("live_clock")
        )
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (next == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("Sin próximo despertar", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Crea o activa una alarma",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = DespiertaAccent.copy(alpha = 0.14f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Alarm,
                                    contentDescription = null,
                                    tint = DespiertaAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                next.alarm.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                formatOccurrence(next.triggerAtMillis),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("FALTAN", color = DespiertaAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                formatCountdown(next.triggerAtMillis - nowMillis),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.testTag("next_alarm_countdown")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmModel,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val active = alarm.enabled || alarm.snoozedUntilMillis != null
    val containerColor by animateColorAsState(
        targetValue = if (active) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.1f),
        animationSpec = tween(160),
        label = "alarm_card"
    )
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (active) Color.White.copy(alpha = 0.1f) else Color.Transparent
        )
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("alarm_toggle_${alarm.id}")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        alarm.label,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        alarmScheduleLabel(alarm),
                        color = if (active) DespiertaAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (alarm.mathChallengeEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "DESAFÍO",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Eliminar ${alarm.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmEmptyState(onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = DespiertaAccent.copy(alpha = 0.12f)) {
                Icon(
                    Icons.Rounded.AlarmAdd,
                    contentDescription = null,
                    tint = DespiertaAccent,
                    modifier = Modifier.padding(13.dp).size(30.dp)
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("Tu mañana empieza aquí", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "Configura una alarma puntual, semanal o por turnos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Crear alarma")
            }
        }
    }
}

@Composable
private fun ShiftScreen(
    alarms: List<AlarmModel>,
    nowMillis: Long,
    onAddShiftAlarm: () -> Unit,
    onEditAlarm: (AlarmModel) -> Unit,
    onToggleAlarm: (AlarmModel, Boolean) -> Unit
) {
    val shiftAlarms = alarms.filter { it.shiftPattern != null }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("shift_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(vertical = 3.dp)) {
                Text("Turnos inteligentes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Visualiza trabajo, descanso y los próximos despertares.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
        if (shiftAlarms.isEmpty()) {
            item { ShiftEmptyState(onAddShiftAlarm) }
        } else {
            items(shiftAlarms, key = AlarmModel::id) { alarm ->
                ShiftAlarmCard(
                    alarm = alarm,
                    nowMillis = nowMillis,
                    onEdit = { onEditAlarm(alarm) },
                    onToggle = { onToggleAlarm(alarm, it) }
                )
            }
        }
    }
}

@Composable
private fun ShiftEmptyState(onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DespiertaHero),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, DespiertaAccent.copy(alpha = 0.24f))
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = DespiertaAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("Organiza tu ciclo laboral", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Empieza con 12×4, 7×7 o define un ciclo personalizado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(15.dp))
            Button(onClick = onAdd) { Text("Crear alarma de turno") }
        }
    }
}

@Composable
private fun ShiftAlarmCard(
    alarm: AlarmModel,
    nowMillis: Long,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val pattern = alarm.shiftPattern ?: return
    val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val dates = remember(today, pattern) { (0L..13L).map(today::plusDays) }
    val upcoming = remember(alarm, nowMillis / 60_000L) {
        upcomingShiftOccurrences(alarm, count = 3, nowMillis = nowMillis)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (alarm.enabled) DespiertaAccent.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(alarm.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "%02d:%02d · %d días trabajo / %d descanso".format(
                            alarm.hour,
                            alarm.minute,
                            pattern.workDays,
                            pattern.restDays
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Editar ${alarm.label}")
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Text(
                "PRÓXIMOS 14 DÍAS",
                color = DespiertaAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(dates, key = LocalDate::toEpochDay) { date ->
                    ShiftDay(date = date, kind = shiftDayKind(pattern, date), isToday = date == today)
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Próximas activaciones", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (upcoming.isEmpty()) {
                    Text(
                        if (alarm.enabled) "No se pudo calcular" else "Alarma pausada",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        upcoming.joinToString("  ·  ") { formatCompactOccurrence(it) },
                        color = DespiertaAccent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftDay(date: LocalDate, kind: ShiftDayKind, isToday: Boolean) {
    val isWork = kind == ShiftDayKind.WORK
    val background = when (kind) {
        ShiftDayKind.WORK -> DespiertaAccent
        ShiftDayKind.EXCEPTION -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (kind) {
        ShiftDayKind.WORK -> DespiertaOnAccent
        ShiftDayKind.EXCEPTION -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EE", SPANISH)).uppercase(SPANISH).take(2),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
        Text(date.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = foreground)
        Text(
            when (kind) {
                ShiftDayKind.WORK -> "TRAB"
                ShiftDayKind.EXCEPTION -> "LIBRE"
                ShiftDayKind.BEFORE_CYCLE -> "ANTES"
                ShiftDayKind.INVALID -> "ERROR"
                ShiftDayKind.REST -> "DESC"
            },
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
        if (isToday) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isWork) DespiertaOnAccent else DespiertaAccent)
            )
        } else {
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    permissionState: PermissionUiState,
    hapticsEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onRequestAlarmChannel: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(vertical = 3.dp)) {
                Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Permisos, respuesta táctil y estado de la aplicación.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
        item {
            SettingsGroup(title = "FIABILIDAD") {
                if (permissionState.pendingCount == 0) {
                    SettingsStatusRow(
                        icon = Icons.Rounded.CheckCircle,
                        title = "Todo listo",
                        detail = "Android permite notificar y mostrar tus alarmas.",
                        accent = true
                    )
                } else {
                    if (permissionState.needsExactAlarm) {
                        PermissionRow(
                            title = "Alarmas exactas",
                            detail = "Permite registrar la hora exacta incluso durante Doze",
                            onClick = onRequestExactAlarm
                        )
                    }
                    if (permissionState.needsNotifications) {
                        PermissionRow(
                            title = "Notificaciones",
                            detail = "Actívalas para ver Detener y Posponer mientras suena",
                            onClick = onRequestNotifications
                        )
                    }
                    if (permissionState.needsFullScreen) {
                        PermissionRow(
                            title = "Pantalla completa",
                            detail = "Autoriza mostrar la alarma sobre la pantalla bloqueada",
                            onClick = onRequestFullScreen
                        )
                    }
                    if (permissionState.needsAlarmChannel) {
                        PermissionRow(
                            title = "Canal sin prioridad alta",
                            detail = "Restaura la prioridad de Alarmas activas en Android",
                            onClick = onRequestAlarmChannel
                        )
                    }
                }
                SettingsStatusRow(
                    icon = Icons.Rounded.Shield,
                    title = "Recuperación automática",
                    detail = "Se reprograman tras reiniciar, actualizar o cambiar la hora. Algunos fabricantes requieren permitir actividad en segundo plano."
                )
            }
        }
        item {
            SettingsGroup(title = "EXPERIENCIA") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Vibration, contentDescription = null, tint = DespiertaAccent)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Respuesta háptica", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Vibración breve al usar los controles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = hapticsEnabled, onCheckedChange = onHapticsChanged)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                SettingsStatusRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Sonido bajo tu control",
                    detail = "Despierta no cambia el volumen ni ignora No molestar; revisa esos ajustes antes de dormir."
                )
            }
        }
        item {
            SettingsGroup(title = "APLICACIÓN") {
                Row(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(13.dp), color = DespiertaAccent, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.NightsStay, contentDescription = null, tint = DespiertaOnAccent)
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Despierta ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                        Text(
                            "Compilación ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_COMMIT}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            color = DespiertaAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DespiertaAccent)
    }
}

@Composable
private fun SettingsStatusRow(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (accent) DespiertaAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (accent) DespiertaAccent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DateBadge(date: LocalDate) {
    Text(
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
        color = DespiertaAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ReminderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun AlarmEditor(
    initial: AlarmModel,
    isNew: Boolean,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (AlarmModel) -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                    }
                    Text(
                        if (isNew) "Nueva alarma" else "Editar alarma",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val saveValue = initial.copy(
                            label = initial.label,
                            minute = initial.minute,
                            hour = initial.hour
                        )
                        onSave(saveValue)
                    }) {
                        Text("Guardar")
                    }
                }

                val timePicker = remember { initial }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%02d:%02d".format(timePicker.hour, timePicker.minute),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                OutlinedTextField(
                    value = initial.label,
                    onValueChange = {},
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Días", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom").forEachIndexed { index, label ->
                        ReminderChip(
                            label = label,
                            selected = initial.days.contains(index + 1),
                            onClick = {}
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibración", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = true, onCheckedChange = {})
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Selecciona tono")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(initial.soundUri))
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Seleccionar tono")
                }
            }
        }
    }
}

private val SPANISH = Locale("es", "ES")

private fun formatCivilDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)
).replaceFirstChar { it.uppercase() }

private fun formatOccurrence(triggerAtMillis: Long): String {
    val instant = Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault())
    return instant.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", SPANISH))
}

private fun formatCountdown(ms: Long): String {
    val remainingMinutes = (ms / 60000L).coerceAtLeast(0L)
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatSleepDuration(ms: Long): String {
    val totalMinutes = (ms / 60000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun alarmScheduleLabel(alarm: AlarmModel): String = when {
    alarm.shiftPattern != null -> "Turno ${alarm.shiftPattern.workDays}x${alarm.shiftPattern.restDays}"
    alarm.days.isEmpty() -> "Una vez"
    else -> "Repetición semanal"
}

@Composable
fun DespiertaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9D8CFF),
            onPrimary = Color(0xFF17122E),
            background = Color(0xFF050A14),
            surface = Color(0xFF101827),
            surfaceVariant = Color(0xFF1B2435),
            onSurface = Color(0xFFE7EAF5),
            onSurfaceVariant = Color(0xFFB4BCD0),
            outline = Color(0xFF3D4D66),
            error = Color(0xFFFF7C7C),
            tertiaryContainer = Color(0xFF1D2333)
        ),
        content = content
    )
}

val DespiertaAccent = Color(0xFF8ED7FF)
val DespiertaOnAccent = Color(0xFF071422)
val DespiertaSky = Color(0xFF8ED7FF)
val DespiertaHero = Color(0xFF0A1327)
val DespiertaSleepSurface = Color(0xFF101A2E)
val DespiertaActiveSurface = Color(0xFF101C2B)
