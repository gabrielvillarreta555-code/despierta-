package com.example.despierta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Keep the controls visible while the alarm is active.
            }
        })

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = AlarmStore(this).find(id)
        AlarmLog.info("alarm_screen_opened", "id=$id found=${alarm != null}")
        setContent { RingingScreen(alarm, id) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    @Composable
    private fun RingingScreen(alarm: AlarmModel?, id: Int) {
        val challenge = rememberSaveable(id) { MathChallenge.create(id) }
        var answer by rememberSaveable(id) { mutableStateOf("") }
        var errorText by rememberSaveable(id) { mutableStateOf<String?>(null) }
        var operationError by rememberSaveable(id) { mutableStateOf<String?>(null) }
        val keyboard = LocalSoftwareKeyboardController.current

        fun stopIfAllowed() {
            if (alarm?.mathChallengeEnabled != true || challenge.accepts(answer)) {
                keyboard?.hide()
                command(AlarmService.ACTION_STOP, id)
            } else {
                errorText = "Respuesta incorrecta. Inténtalo otra vez."
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF89B4FA),
                onPrimary = Color(0xFF040A18),
                background = Color(0xFF070C15),
                surface = Color(0xFF101729),
                onSurface = Color(0xFFF4F1EB),
                onSurfaceVariant = Color(0xFFAAAEB7)
            )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF070B19), Color(0xFF141933))
                        )
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "DESPIERTA ${BuildConfig.VERSION_NAME}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.AlarmOn,
                            contentDescription = null,
                            modifier = Modifier.padding(20.dp).size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val timeSize = when {
                            maxWidth < 300.dp -> 58.sp
                            maxWidth < 360.dp -> 68.sp
                            else -> 80.sp
                        }
                        Text(
                            alarm?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = timeSize,
                            lineHeight = timeSize,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            modifier = Modifier.testTag("ringing_time")
                        )
                    }
                    Text(
                        alarm?.label ?: "Alarma",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(if (alarm?.mathChallengeEnabled == true) 28.dp else 52.dp))

                    if (alarm?.mathChallengeEnabled == true) {
                        Text("Resuelve para detener", fontWeight = FontWeight.SemiBold)
                        Text(
                            challenge.question,
                            fontSize = 32.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        OutlinedTextField(
                            value = answer,
                            onValueChange = {
                                answer = it.filter(Char::isDigit).take(4)
                                errorText = null
                            },
                            label = { Text("Respuesta") },
                            isError = errorText != null,
                            supportingText = { errorText?.let { message -> Text(message) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { stopIfAllowed() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    Button(
                        onClick = {
                            operationError = null
                            val result = alarm?.let {
                                AlarmScheduler(this@AlarmActivity).snooze(
                                    AlarmStore(this@AlarmActivity).find(id) ?: it,
                                    it.snoozeMinutes
                                )
                            }
                            if (result != null && result != ScheduleResult.FAILED) {
                                command(AlarmService.ACTION_STOP, id)
                            } else {
                                operationError =
                                    "No se pudo posponer. La alarma seguirá activa para que puedas intentarlo de nuevo."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp).testTag("ringing_snooze")
                    ) {
                        Text("Posponer ${alarm?.snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES} minutos")
                    }
                    operationError?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { stopIfAllowed() },
                        modifier = Modifier.fillMaxWidth().height(60.dp).testTag("ringing_stop")
                    ) {
                        Text(if (alarm?.mathChallengeEnabled == true) "Comprobar y detener" else "Detener")
                    }
                }
            }
        }
    }

    private fun command(action: String, id: Int) {
        runCatching {
            startService(
                Intent(this, AlarmService::class.java)
                    .setAction(action)
                    .putExtra(AlarmScheduler.EXTRA_ID, id)
            )
        }.onFailure {
            AlarmLog.error("alarm_command_failed", "id=$id action=$action", it)
        }
        finishAndRemoveTask()
    }
}

data class MathChallenge(val left: Int, val right: Int) : java.io.Serializable {
    val question: String get() = "$left + $right = ?"

    fun accepts(value: String): Boolean = value.toIntOrNull() == left + right

    companion object {
        fun create(seed: Int): MathChallenge {
            val stableSeed = (seed.toLong() xor System.currentTimeMillis()) and Long.MAX_VALUE
            val left = 10 + (stableSeed % 40).toInt()
            val right = 10 + ((stableSeed / 41) % 40).toInt()
            return MathChallenge(left, right)
        }
    }
}
