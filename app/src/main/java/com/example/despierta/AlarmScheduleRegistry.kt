package com.example.despierta

import android.annotation.SuppressLint
import android.content.Context

/**
 * Records the exact occurrence represented by each PendingIntent.
 *
 * AlarmManager identifies PendingIntents without looking at their extras. Keeping the expected
 * trigger separately lets the receiver reject an old delivery after an alarm was edited,
 * snoozed, cancelled, or restored during boot.
 */
internal class AlarmScheduleRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @SuppressLint("ApplySharedPref") // The token must be durable before AlarmManager can fire.
    fun record(alarmId: Int, triggerAtMillis: Long) {
        synchronized(lock) {
            prefs.edit().putLong(key(alarmId), triggerAtMillis).commit()
        }
    }

    @SuppressLint("ApplySharedPref") // The cancellation tombstone must be immediately visible.
    fun clear(alarmId: Int) {
        synchronized(lock) {
            // Keep a tombstone so a delivery already queued by the system cannot resurrect a
            // cancelled alarm. A future schedule for the same id overwrites this value.
            prefs.edit().putLong(key(alarmId), CANCELLED).commit()
        }
    }

    /** Atomically accepts the current delivery once. Legacy 2.1 intents have no token. */
    @SuppressLint("ApplySharedPref") // Atomic duplicate protection is more important than async IO.
    fun claim(alarmId: Int, triggerAtMillis: Long): Boolean = synchronized(lock) {
        val entryExists = prefs.contains(key(alarmId))
        val stored = prefs.getLong(key(alarmId), CANCELLED)
        if (triggerAtMillis <= 0L) {
            // Accept an occurrence created by 2.1 only if 2.2 has not already replaced it.
            return@synchronized !entryExists
        }
        // Fail open only for schedules created before the registry existed or after an
        // unrecoverable preferences failure. AlarmManager must remain the source of truth.
        if (!entryExists) return@synchronized true
        if (stored != triggerAtMillis) return@synchronized false
        prefs.edit().putLong(key(alarmId), -triggerAtMillis).commit()
        true
    }

    internal fun expectedTrigger(alarmId: Int): Long? = synchronized(lock) {
        prefs.getLong(key(alarmId), CANCELLED).takeIf { it > 0L }
    }

    private fun key(alarmId: Int) = "alarm_$alarmId"

    companion object {
        private const val PREFS_NAME = "alarm_schedule_registry_v2"
        private const val CANCELLED = Long.MIN_VALUE
        private val lock = Any()
    }
}
