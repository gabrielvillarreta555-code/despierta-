package com.example.despierta

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AlarmScreenInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun activeAlarmHasReadableTimeLargeControlsAndProducesRealScreenshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AlarmStore(context)
        val alarm = store.newAlarm().copy(
            hour = 6,
            minute = 45,
            label = "Contraste verificado",
            snoozeMinutes = 10,
            vibrate = false,
            enabled = true
        )
        store.save(alarm)
        val intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)

        try {
            ActivityScenario.launch<AlarmActivity>(intent).use {
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("ringing_time")
                    .assertIsDisplayed()
                    .assertTextEquals("06:45")
                composeRule.onNodeWithTag("ringing_snooze").assertIsDisplayed()
                composeRule.onNodeWithTag("ringing_stop").assertIsDisplayed()

                val screenshot = InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .takeScreenshot()
                val output = File(context.filesDir, SCREENSHOT_NAME)
                output.outputStream().use { stream ->
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                assertTrue(output.isFile && output.length() > 0L)
            }
        } finally {
            store.delete(alarm.id)
        }
    }

    companion object {
        const val SCREENSHOT_NAME = "Despierta-2.4-Alarma-activa.png"
    }
}
