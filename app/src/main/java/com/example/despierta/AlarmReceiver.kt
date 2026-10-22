package com.example.despierta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = AlarmStore(context).find(id) ?: return
        context.startForegroundService(Intent(context, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_ID, id))
        if (alarm.days.isEmpty()) AlarmStore(context).save(alarm.copy(enabled = false))
        else AlarmScheduler(context).schedule(alarm)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmStore(context).getAll().filter { it.enabled }.forEach { AlarmScheduler(context).schedule(it) }
    }
}
