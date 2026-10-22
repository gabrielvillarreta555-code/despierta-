package com.example.despierta

import android.content.Context
import android.media.RingtoneManager
import org.json.JSONArray
import org.json.JSONObject

data class AlarmModel(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Alarma",
    val days: Set<Int> = emptySet(), // Calendar.SUNDAY..SATURDAY
    val soundUri: String = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString(),
    val enabled: Boolean = true
)

class AlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun getAll(): List<AlarmModel> = runCatching {
        val source = JSONArray(prefs.getString("items", "[]"))
        List(source.length()) { index ->
            val item = source.getJSONObject(index)
            val jsonDays = item.optJSONArray("days") ?: JSONArray()
            AlarmModel(
                id = item.getInt("id"), hour = item.getInt("hour"), minute = item.getInt("minute"),
                label = item.optString("label", "Alarma"),
                days = (0 until jsonDays.length()).map { jsonDays.getInt(it) }.toSet(),
                soundUri = item.optString("soundUri", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()),
                enabled = item.optBoolean("enabled", true)
            )
        }
    }.getOrDefault(emptyList())

    fun find(id: Int) = getAll().firstOrNull { it.id == id }

    fun save(alarm: AlarmModel) {
        val alarms = getAll().filterNot { it.id == alarm.id } + alarm
        write(alarms.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    fun delete(id: Int) = write(getAll().filterNot { it.id == id })

    private fun write(alarms: List<AlarmModel>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(JSONObject().apply {
                put("id", alarm.id); put("hour", alarm.hour); put("minute", alarm.minute)
                put("label", alarm.label); put("days", JSONArray(alarm.days.toList()))
                put("soundUri", alarm.soundUri); put("enabled", alarm.enabled)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
