package com.example.despierta

import android.Manifest
import android.app.AlarmManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlarmApp() }
    }

    @Composable private fun AlarmApp() {
        val store = remember { AlarmStore(this) }
        val scheduler = remember { AlarmScheduler(this) }
        var alarms by remember { mutableStateOf(store.getAll()) }
        var editing by remember { mutableStateOf<AlarmModel?>(null) }
        var creating by remember { mutableStateOf(false) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        DespiertaTheme {
            Scaffold(
                topBar = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NightsStay, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Despierta", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(nextAlarmText(alarms), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                },
                floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Nueva alarma") } }
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { PermissionCard() }
                    if (alarms.isEmpty()) item { EmptyState { creating = true } }
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(alarm,
                            onClick = { editing = alarm },
                            onToggle = { enabled ->
                                val updated = alarm.copy(enabled = enabled); store.save(updated); scheduler.schedule(updated); alarms = store.getAll()
                            },
                            onDelete = { scheduler.cancel(alarm.id); store.delete(alarm.id); alarms = store.getAll() })
                    }
                }
            }
            val target = editing ?: if (creating) AlarmModel() else null
            if (target != null) AlarmEditor(target, onDismiss = { editing = null; creating = false }) { saved ->
                store.save(saved); scheduler.schedule(saved); alarms = store.getAll(); editing = null; creating = false
            }
        }
    }

    @Composable private fun PermissionCard() {
        val needsExact = Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        if (!needsExact) return
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Row(Modifier.fillMaxWidth().clickable {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Permite alarmas exactas", fontWeight = FontWeight.Bold); Text("Necesario para sonar puntualmente", fontSize = 13.sp) }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
    }

    @Composable private fun AlarmCard(alarm: AlarmModel, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d:%02d".format(alarm.hour, alarm.minute), fontSize = 43.sp, fontWeight = FontWeight.Light,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f)); Switch(alarm.enabled, onCheckedChange = onToggle)
                }
                Text(alarm.label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(daysLabel(alarm.days), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    @Composable private fun EmptyState(onAdd: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AlarmAdd, null, Modifier.size(68.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp)); Text("Aún no hay alarmas", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Crea una para empezar el día a tu hora", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdd) { Text("Crear alarma") }
        }
    }

    @Composable private fun AlarmEditor(initial: AlarmModel, onDismiss: () -> Unit, onSave: (AlarmModel) -> Unit) {
        var hour by remember(initial.id) { mutableIntStateOf(initial.hour) }
        var minute by remember(initial.id) { mutableIntStateOf(initial.minute) }
        var label by remember(initial.id) { mutableStateOf(initial.label) }
        var days by remember(initial.id) { mutableStateOf(initial.days) }
        var soundUri by remember(initial.id) { mutableStateOf(initial.soundUri) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { soundUri = it.toString() }
        }
        val timePickerState = rememberTimePickerState(hour, minute, true)
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(if (initial.label == "Alarma" && initial.days.isEmpty()) "Nueva alarma" else "Editar alarma") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimeInput(timePickerState)
                    OutlinedTextField(label, { label = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Repetir", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("D","L","M","X","J","V","S").forEachIndexed { i, text ->
                            val day = i + 1; FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(text) })
                        }
                    }
                    OutlinedButton(onClick = {
                        picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                        })
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("Elegir sonido") }
                }
            },
            confirmButton = { Button(onClick = { hour = timePickerState.hour; minute = timePickerState.minute; onSave(initial.copy(hour=hour, minute=minute, label=label.ifBlank { "Alarma" }, days=days, soundUri=soundUri, enabled=true)) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    }

    private fun daysLabel(days: Set<Int>) = when {
        days.isEmpty() -> "Una vez"
        days.size == 7 -> "Todos los días"
        days == setOf(2,3,4,5,6) -> "Entre semana"
        else -> listOf("Dom","Lun","Mar","Mié","Jue","Vie","Sáb").filterIndexed { i, _ -> i + 1 in days }.joinToString(" · ")
    }

    private fun nextAlarmText(alarms: List<AlarmModel>): String = if (alarms.none { it.enabled }) "Sin alarmas activas" else "Tu próximo despertar está programado"
}

@Composable private fun DespiertaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF9D8CFF), onPrimary = Color(0xFF17122E),
        background = Color(0xFF0B0D12), surface = Color(0xFF171A21), surfaceVariant = Color(0xFF222630),
        onSurface = Color(0xFFF2F0FA), onSurfaceVariant = Color(0xFFAAA8B3), tertiaryContainer = Color(0xFF3A3020)
    ), content = content)
}
