package com.example.appscheduler

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pichle runs ka chhota log (app mein "Log dekho"), taake pata chale schedule par kya hua. */
object RunLog {
    private const val KEY = "log"

    private fun sp(c: Context) = c.getSharedPreferences("runlog", Context.MODE_PRIVATE)

    fun add(ctx: Context, msg: String) {
        try {
            val t = SimpleDateFormat("dd HH:mm:ss", Locale.US).format(Date())
            val old = (sp(ctx).getString(KEY, "") ?: "").split("\n").filter { it.isNotBlank() }
            val lines = (old + "$t  $msg").takeLast(60)
            sp(ctx).edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (_: Exception) {
        }
    }

    fun hms(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    fun text(ctx: Context): String {
        val s = sp(ctx).getString(KEY, "") ?: ""
        return if (s.isBlank()) "Abhi koi log nahi." else s
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }
}
