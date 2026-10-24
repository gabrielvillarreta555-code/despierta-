package com.example.despierta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val store = AlarmStore(context)
        val alarm = store.find(id)
        if (id <= 0 || alarm == null) {
            AlarmLog.warning("receiver_ignored", "id=$id reason=missing_alarm")
            return
        }

        val now = System.currentTimeMillis()
        val snoozedUntil = alarm.snoozedUntilMillis
        val isSnooze = snoozedUntil != null

        // Ignore a stale broadcast from a schedule that was replaced by a later snooze.
        if (snoozedUntil != null && snoozedUntil > now + EARLY_TOLERANCE_MILLIS) {
            AlarmLog.warning(
                "receiver_early",
                "id=$id expectedAt=$snoozedUntil receivedAt=$now"
            )
            AlarmScheduler(context).schedule(alarm)
            return
        }
        if (!alarm.enabled && !isSnooze) {
            AlarmLog.warning("receiver_ignored", "id=$id reason=disabled")
            AlarmScheduler(context).cancel(id)
            return
        }

        AlarmLog.info("receiver_fired", "id=$id snooze=$isSnooze receivedAt=$now")
        val updated = stateAfterFiring(alarm, isSnooze)
        store.save(updated)

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_RING)
                    .putExtra(AlarmScheduler.EXTRA_ID, id)
            )
            AlarmLog.info("service_start_requested", "id=$id")
        } catch (error: RuntimeException) {
            AlarmLog.error("service_start_failed", "id=$id", error)
        }

        // setAlarmClock() is one-shot. Re-register repeating alarms immediately;
        // a one-time alarm stays disabled but can still be explicitly snoozed.
        if (updated.enabled && isRecurring(updated)) {
            AlarmScheduler(context).schedule(updated)
        }
    }

    companion object {
        private const val EARLY_TOLERANCE_MILLIS = 30_000L

        internal fun isRecurring(alarm: AlarmModel): Boolean =
            alarm.days.isNotEmpty() || alarm.shiftPattern?.isValid == true

        internal fun stateAfterFiring(alarm: AlarmModel, isSnooze: Boolean): AlarmModel = when {
            isSnooze -> alarm.copy(snoozedUntilMillis = null)
            isRecurring(alarm) -> alarm.copy(snoozedUntilMillis = null)
            else -> alarm.copy(enabled = false, snoozedUntilMillis = null)
        }
    }
}
