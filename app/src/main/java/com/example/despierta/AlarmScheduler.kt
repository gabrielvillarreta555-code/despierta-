package com.example.despierta

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: AlarmModel) {
        cancel(alarm.id)
        if (!alarm.enabled) return
        val trigger = nextTrigger(alarm)
        val pending = pendingIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pending)
        }
    }

    fun cancel(id: Int) = manager.cancel(pendingIntent(id, PendingIntent.FLAG_NO_CREATE) ?: return)

    private fun pendingIntent(id: Int, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, id, Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        flag or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextTrigger(alarm: AlarmModel): Calendar {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour); set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (alarm.days.isEmpty()) {
            if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate
        }
        repeat(8) {
            if (candidate.after(now) && candidate.get(Calendar.DAY_OF_WEEK) in alarm.days) return candidate
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate
    }

    companion object { const val EXTRA_ID = "alarm_id" }
}
