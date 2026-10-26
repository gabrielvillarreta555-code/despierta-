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
import androidx.compose.foundation.Canvas
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

        // Sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF040614), // Deep night sky top
                    Color(0xFF0D122B), // Violet dark mid
                    Color(0xFF1B1F3C)  // Horizon
                )
            )
        )

        // Moon (Warm elegant light)
        drawCircle(
            color = Color(0xFFFFECCC), // Warm soft moon color
            radius = width * 0.15f,
            center = androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.15f)
        )
        // Moon glow
        drawCircle(
            color = Color(0x20FFECCC),
            radius = width * 0.25f,
            center = androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.15f)
        )

        // Distant Mountains (soft organic path)
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

        // Mid Mountains
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

        // Lake reflections / Front layer
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
    // Minimalist live clock without the card background
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

        // Inside the Next Alarm section we can put a very subtle translucent card
        // to group the next alarm info separate from the clock
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
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
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
        Icon(icon, contentDescription = null, tint = if (accent) DespiertaAccent else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditor(
    initial: AlarmModel,
    isNew: Boolean,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (AlarmModel) -> Unit
) {
    val validInitialShift = initial.shiftPattern?.takeIf { it.isValid }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    var soundUri by remember(initial.id) { mutableStateOf(initial.soundUri) }
    var vibrate by remember(initial.id) { mutableStateOf(initial.vibrate) }
    var snoozeMinutes by remember(initial.id) {
        mutableIntStateOf(AlarmStore.normalizeSnoozeMinutes(initial.snoozeMinutes))
    }
    var mathChallenge by remember(initial.id) { mutableStateOf(initial.mathChallengeEnabled) }
    var useShift by remember(initial.id) { mutableStateOf(initial.shiftPattern != null) }
    var workDays by remember(initial.id) { mutableStateOf(validInitialShift?.workDays?.toString() ?: "12") }
    var restDays by remember(initial.id) { mutableStateOf(validInitialShift?.restDays?.toString() ?: "4") }
    var startEpochDay by remember(initial.id) {
        mutableLongStateOf(validInitialShift?.startEpochDay ?: LocalDate.now().toEpochDay())
    }
    val context = LocalContext.current
    val uses24HourClock = DateFormat.is24HourFormat(context)
    var selectedHour by remember(initial.id) { mutableIntStateOf(initial.hour) }
    var selectedMinute by remember(initial.id) { mutableIntStateOf(initial.minute) }
    var manualEntryVisible by remember(initial.id) { mutableStateOf(false) }
    var manualTime by remember(initial.id, uses24HourClock) {
        mutableStateOf(formatManualTime(initial.hour, initial.minute, uses24HourClock))
    }

    val shiftPatternCandidate = if (useShift) {
        val work = workDays.toIntOrNull()
        val rest = restDays.toIntOrNull()
        if (work != null && rest != null) {
            ShiftPattern(
                startEpochDay = startEpochDay,
                workDays = work,
                restDays = rest,
                exceptionEpochDays = validInitialShift?.exceptionEpochDays ?: emptySet()
            )
        } else {
            null
        }
    } else {
        null
    }
    val shiftInputValid = !useShift || shiftPatternCandidate?.isValid == true
    val manualTimeValid = !manualEntryVisible || parseManualTime(manualTime, uses24HourClock) != null
    val tonePreview = remember(initial.id) { AlarmTonePreview(context) }
    var tonePreviewState by remember(initial.id) { mutableStateOf(TonePreviewState.IDLE) }
    DisposableEffect(tonePreview) {
        onDispose { tonePreview.stop() }
    }
    val toneTitle = remember(soundUri) {
        runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context)
        }.getOrNull() ?: "Tono predeterminado"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
            tonePreview.stop()
            soundUri = uri.toString()
        }
    }

    fun save() {
        val pattern = shiftPatternCandidate
        if (useShift && pattern == null) return
        onSave(
            initial.copy(
                hour = selectedHour,
                minute = selectedMinute,
                label = label.trim().ifBlank { "Alarma" },
                days = if (useShift) emptySet() else days,
                shiftPattern = pattern,
                soundUri = soundUri.ifBlank { AlarmStore.defaultAlarmUri() },
                vibrate = vibrate,
                snoozeMinutes = snoozeMinutes,
                enabled = true,
                mathChallengeEnabled = mathChallenge,
                snoozedUntilMillis = null
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (isNew) "Nueva alarma" else "Editar alarma", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Hora, repetición y sonido", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Button(onClick = ::save, enabled = shiftInputValid && manualTimeValid) {
                        Text("Guardar")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EditorSection("HORA") {
                        TimeWheelPicker(
                            hour24 = selectedHour,
                            minute = selectedMinute,
                            is24Hour = uses24HourClock,
                            hapticsEnabled = hapticsEnabled,
                            onTimeChanged = { hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                                manualTime = formatManualTime(hour, minute, uses24HourClock)
                            }
                        )
                        TextButton(
                            onClick = {
                                manualEntryVisible = !manualEntryVisible
                                manualTime = formatManualTime(
                                    selectedHour,
                                    selectedMinute,
                                    uses24HourClock
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (manualEntryVisible) "Ocultar entrada manual" else "Introducir hora manualmente")
                        }
                        if (manualEntryVisible) {
                            OutlinedTextField(
                                value = manualTime,
                                onValueChange = { entered ->
                                    manualTime = entered.take(11)
                                    parseManualTime(entered, uses24HourClock)?.let { parsed ->
                                        selectedHour = parsed.hour24
                                        selectedMinute = parsed.minute
                                    }
                                },
                                label = {
                                    Text(if (uses24HourClock) "Hora (HH:MM)" else "Hora (HH:MM AM/PM)")
                                },
                                isError = !manualTimeValid,
                                supportingText = {
                                    if (!manualTimeValid) {
                                        Text(
                                            if (uses24HourClock) {
                                                "Usa una hora entre 00:00 y 23:59"
                                            } else {
                                                "Usa una hora como 07:30 AM o 07:30 PM"
                                            }
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth().testTag("manual_time_input")
                            )
                        }
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it.take(60) },
                            label = { Text("Nombre de la alarma") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    EditorSection("PROGRAMACIÓN") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Turno inteligente", fontWeight = FontWeight.SemiBold)
                                Text("Alterna automáticamente trabajo y descanso", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(checked = useShift, onCheckedChange = { useShift = it })
                        }
                        if (useShift) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = workDays,
                                    onValueChange = { workDays = it.filter(Char::isDigit).take(3) },
                                    label = { Text("Trabajo") },
                                    suffix = { Text("días") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = (workDays.toIntOrNull() ?: 0) < 1,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = restDays,
                                    onValueChange = { restDays = it.filter(Char::isDigit).take(3) },
                                    label = { Text("Descanso") },
                                    suffix = { Text("días") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = (restDays.toIntOrNull() ?: 0) < 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (!shiftInputValid) {
                                Text(
                                    "Usa al menos un día de trabajo y uno de descanso; el ciclo admite hasta 366 días.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                            Text("Inicio del ciclo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { startEpochDay-- },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Día anterior")
                                }
                                OutlinedButton(
                                    onClick = { startEpochDay = LocalDate.now().toEpochDay() },
                                    modifier = Modifier.widthIn(min = 180.dp)
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatCivilDate(LocalDate.ofEpochDay(startEpochDay)))
                                }
                                IconButton(
                                    onClick = { startEpochDay++ },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Día siguiente")
                                }
                            }
                            Text(
                                "Toca la fecha para volver a hoy; usa las flechas para sincronizar el primer día de trabajo.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        } else {
                            Text("Repetir", fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
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
                            Text(
                                if (days.isEmpty()) "Sonará una sola vez" else daysLabel(days),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    EditorSection("SONIDO Y CONTROL") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Desafío matemático", fontWeight = FontWeight.SemiBold)
                                Text("Será obligatorio para detener", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(checked = mathChallenge, onCheckedChange = { mathChallenge = it })
                        }
                        Text("Posponer", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SUPPORTED_SNOOZE_MINUTES.sorted().forEach { minutes ->
                                FilterChip(
                                    selected = snoozeMinutes == minutes,
                                    onClick = { snoozeMinutes = minutes },
                                    label = { Text("$minutes min") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("snooze_$minutes")
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Vibration, contentDescription = null, tint = DespiertaAccent)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Vibración", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (vibrate) "Vibrará mientras suena" else "Desactivada para esta alarma",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = vibrate,
                                onCheckedChange = { vibrate = it },
                                modifier = Modifier.testTag("alarm_vibration")
                            )
                        }
                        Text("Tono de alarma", fontWeight = FontWeight.SemiBold)
                        Text(
                            toneTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("selected_tone")
                        )
                        OutlinedButton(
                            onClick = {
                                picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                                })
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Elegir sonido")
                        }
                        OutlinedButton(
                            onClick = {
                                if (
                                    tonePreviewState == TonePreviewState.PREPARING ||
                                    tonePreviewState == TonePreviewState.PLAYING ||
                                    tonePreviewState == TonePreviewState.PLAYING_FALLBACK
                                ) {
                                    tonePreview.stop()
                                } else {
                                    tonePreview.play(soundUri) { tonePreviewState = it }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("preview_tone")
                        ) {
                            val previewActive = tonePreviewState == TonePreviewState.PREPARING ||
                                tonePreviewState == TonePreviewState.PLAYING ||
                                tonePreviewState == TonePreviewState.PLAYING_FALLBACK
                            Icon(
                                if (previewActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (previewActive) "Detener preescucha" else "Preescuchar tono")
                        }
                        when (tonePreviewState) {
                            TonePreviewState.PLAYING_FALLBACK -> Text(
                                "El tono elegido no está disponible; se escucha el predeterminado.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                            TonePreviewState.FAILED -> Text(
                                "No se pudo reproducir el tono. Android usará un sonido predeterminado al sonar.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                            else -> Unit
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(
            title,
            color = DespiertaAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = { content() }
            )
        }
    }
}

@Composable
internal fun DespiertaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DespiertaAccent,
            onPrimary = DespiertaOnAccent,
            primaryContainer = Color(0xFF332B57),
            onPrimaryContainer = Color(0xFFE9DDFF),
            secondary = DespiertaSky,
            onSecondary = Color(0xFF001B3D),
            secondaryContainer = Color(0xFF18345E),
            onSecondaryContainer = Color(0xFFD7E3FF),
            background = Color(0xFF070B19),
            surface = Color(0xFF101524),
            surfaceVariant = Color(0xFF1B2136),
            onSurface = Color(0xFFF4F1FF),
            onSurfaceVariant = Color(0xFFBEC3D8),
            outline = Color(0xFF555D78),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF5B2525),
            onErrorContainer = Color(0xFFFFDAD6)
        ),
        content = content
    )
}

private fun alarmScheduleLabel(alarm: AlarmModel): String {
    val schedule = alarm.shiftPattern?.let { "Turno ${it.workDays}×${it.restDays}" } ?: daysLabel(alarm.days)
    return if (alarm.snoozedUntilMillis != null) "Pospuesta · $schedule" else schedule
}

internal fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> "Una vez"
    days.size == 7 -> "Todos los días"
    days == setOf(2, 3, 4, 5, 6) -> "Entre semana"
    else -> listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        .filterIndexed { index, _ -> index + 1 in days }
        .joinToString(" · ")
}

private fun formatCivilDate(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH))
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(SPANISH) else it.toString() }

private fun formatOccurrence(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", SPANISH))

private fun formatCompactOccurrence(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE d · HH:mm", SPANISH))

internal fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1_000L
    if (totalSeconds == 0L) return "Ahora"
    val days = totalSeconds / 86_400L
    val hours = totalSeconds % 86_400L / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (days > 0L) {
        "%d d · %02d:%02d:%02d".format(days, hours, minutes, seconds)
    } else {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

internal fun formatSleepDuration(remainingMillis: Long): String {
    val totalMinutes = (remainingMillis.coerceAtLeast(0L) + 59_999L) / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = totalMinutes % (24L * 60L) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L -> "%d d · %d h %02d min".format(days, hours, minutes)
        hours > 0L -> "%d h %02d min".format(hours, minutes)
        minutes > 0L -> "%d min".format(minutes)
        else -> "Ahora"
    }
}

private val SPANISH = Locale("es", "ES")
private val DespiertaAccent = Color(0xFFC4B5FD)
private val DespiertaOnAccent = Color(0xFF21153F)
private val DespiertaSky = Color(0xFF82B1FF)
private val DespiertaHero = Color(0xFF0D1630)
private val DespiertaSleepSurface = Color(0xFF101B35)
private val DespiertaActiveSurface = Color(0xFF171A34)
