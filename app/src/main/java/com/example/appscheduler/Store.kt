package com.example.appscheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** closeMode: 0 = close nahi karna, 1 = soft close (Home + background process kill), 2 = Force stop (App Info screen) */
const val CLOSE_NONE = 0
const val CLOSE_SOFT = 1
const val CLOSE_FORCE = 2

data class Schedule(
    val id: Int,
    val pkg: String,
    val label: String,
    val startH: Int,
    val startM: Int,
    val endH: Int,
    val endM: Int,
    val enabled: Boolean,
    val closeMode: Int = CLOSE_SOFT,
    /** "" = alarm nahi, "default" = phone ka default alarm, warna ringtone ka Uri string */
    val alarm: String = ""
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
                    o.getBoolean("on"),
                    o.optInt("cm", CLOSE_FORCE), // purane schedules pehle Force stop hi karte the
                    o.optString("al", "")
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
            o.put("cm", s.closeMode)
            o.put("al", s.alarm)
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

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Phone lock ho to launch ke waqt screen on karke unlock prompt dikhana */
    fun unlockPrompt(c: Context): Boolean = sp(c).getBoolean("unlockPrompt", true)

    fun setUnlockPrompt(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("unlockPrompt", on).apply()
    }

    /** Unlock (app khulne) ke baad phone itne second tak lock/screen off na ho */
    fun keepAwakeSec(c: Context): Int = sp(c).getInt("keepAwake", 60)

    /** Phone bilkul lock na ho (screen on rahe jab tak user band na kare) */
    fun neverLock(c: Context): Boolean = sp(c).getBoolean("neverLock", false)

    /** Us time ke baad phone khud lock karna (agar schedule ke waqt locked tha) */
    fun relock(c: Context): Boolean = sp(c).getBoolean("relock", true)

    fun saveAll(c: Context, keepAwake: Int, neverLock: Boolean, relock: Boolean) {
        sp(c).edit()
            .putInt("keepAwake", keepAwake)
            .putBoolean("neverLock", neverLock)
            .putBoolean("relock", relock)
            .apply()
    }

    // ---- Andar ki halat (settings nahi) ----
    fun openedAt(c: Context): Long = sp(c).getLong("openedAt", 0L)

    fun setOpenedAt(c: Context, t: Long) {
        sp(c).edit().putLong("openedAt", t).apply()
    }

    fun relockPending(c: Context): Boolean = sp(c).getBoolean("relockPending", false)

    fun setRelockPending(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("relockPending", on).apply()
    }
}
