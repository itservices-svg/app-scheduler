package com.example.appscheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Schedule(
    val id: Int,
    val pkg: String,
    val label: String,
    val startH: Int,
    val startM: Int,
    val endH: Int,
    val endM: Int,
    val enabled: Boolean
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("schedules", Context.MODE_PRIVATE)

    fun all(c: Context): List<Schedule> {
        val arr = JSONArray(sp(c).getString("list", "[]"))
        val out = ArrayList<Schedule>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Schedule(
                    o.getInt("id"), o.getString("pkg"), o.getString("label"),
                    o.getInt("sh"), o.getInt("sm"), o.getInt("eh"), o.getInt("em"),
                    o.getBoolean("on")
                )
            )
        }
        return out
    }

    private fun saveAll(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("pkg", s.pkg)
            o.put("label", s.label)
            o.put("sh", s.startH)
            o.put("sm", s.startM)
            o.put("eh", s.endH)
            o.put("em", s.endM)
            o.put("on", s.enabled)
            arr.put(o)
        }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun get(c: Context, id: Int): Schedule? = all(c).firstOrNull { it.id == id }

    fun nextId(c: Context): Int {
        val n = sp(c).getInt("nextId", 1)
        sp(c).edit().putInt("nextId", n + 1).apply()
        return n
    }

    fun add(c: Context, s: Schedule) = saveAll(c, all(c) + s)

    fun update(c: Context, s: Schedule) =
        saveAll(c, all(c).map { if (it.id == s.id) s else it })

    fun remove(c: Context, id: Int) = saveAll(c, all(c).filter { it.id != id })
}
