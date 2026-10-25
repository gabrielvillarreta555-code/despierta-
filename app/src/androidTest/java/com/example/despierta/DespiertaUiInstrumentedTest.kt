package com.example.despierta

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class DespiertaUiInstrumentedTest {
    @get:Rule(order = 0)
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun versionAndPrimaryNavigationAreVisible() {
        composeRule.onNodeWithText("DESPIERTA").assertIsDisplayed()
        composeRule.onNodeWithText("2.2").assertIsDisplayed()
        composeRule.onNodeWithTag("live_clock").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_shifts").performClick()
        composeRule.onNodeWithTag("shift_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Despierta 2.2").assertIsDisplayed()
    }

    @Test
    fun alarmEditorShowsWheelPickerAndManualEntry() {
        composeRule.onNodeWithTag("add_alarm").performClick()

        composeRule.onNodeWithTag("time_wheel_hour").assertIsDisplayed()
        composeRule.onNodeWithTag("time_wheel_minute").assertIsDisplayed()
        composeRule.onNodeWithText("Introducir hora manualmente").performClick()
        composeRule.onNodeWithTag("manual_time_input").assertIsDisplayed()
    }
}
