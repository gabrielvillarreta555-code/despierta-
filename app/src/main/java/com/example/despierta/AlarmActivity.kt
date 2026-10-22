package com.example.despierta

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = AlarmStore(this).find(id)
        setContent { RingingScreen(alarm?.label ?: "Alarma", id) }
    }

    @Composable private fun RingingScreen(label: String, id: Int) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9D8CFF), background = Color(0xFF0B0D12))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.AlarmOn, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(28.dp))
                    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 72.sp, fontWeight = FontWeight.Light)
                    Text(label, fontSize = 22.sp, color = Color.LightGray)
                    Spacer(Modifier.height(64.dp))
                    Button(onClick = { command(AlarmService.ACTION_SNOOZE, id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Posponer 5 minutos") }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { command(AlarmService.ACTION_STOP, id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Detener") }
                }
            }
        }
    }

    private fun command(action: String, id: Int) {
        startService(Intent(this, AlarmService::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_ID, id))
        finishAndRemoveTask()
    }
}
