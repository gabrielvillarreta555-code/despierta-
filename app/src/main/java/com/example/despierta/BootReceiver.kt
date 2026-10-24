package com.example.despierta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) {
            AlarmLog.warning("reschedule_ignored", "action=${intent.action}")
            return
        }

        val alarms = AlarmStore(context).getAll()
        AlarmLog.info("reschedule_started", "action=${intent.action} stored=${alarms.size}")
        AlarmScheduler(context).reconcile(alarms)
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
