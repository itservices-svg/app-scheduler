package com.example.appscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    const val ACTION_LAUNCH = "com.example.appscheduler.LAUNCH"
    const val ACTION_CLOSE = "com.example.appscheduler.CLOSE"
    const val EXTRA_PKG = "pkg"
    const val EXTRA_ID = "id"
    const val EXTRA_REPEAT = "repeat"

    private const val TEST_LAUNCH_CODE = 900001
    private const val TEST_CLOSE_CODE = 900002

    private fun pending(
        ctx: Context, code: Int, action: String, pkg: String, id: Int, repeat: Boolean
    ): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_REPEAT, repeat)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun setAt(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(ctx: Context, s: Schedule) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, s.id * 2, ACTION_LAUNCH, s.pkg, s.id, true))
        am.cancel(pending(ctx, s.id * 2 + 1, ACTION_CLOSE, s.pkg, s.id, true))
    }

    fun scheduleOne(ctx: Context, s: Schedule) {
        cancel(ctx, s)
        if (!s.enabled) return
        setAt(
            ctx, nextTrigger(s.startH, s.startM),
            pending(ctx, s.id * 2, ACTION_LAUNCH, s.pkg, s.id, true)
        )
        setAt(
            ctx, nextTrigger(s.endH, s.endM),
            pending(ctx, s.id * 2 + 1, ACTION_CLOSE, s.pkg, s.id, true)
        )
    }

    fun scheduleAll(ctx: Context) {
        Store.all(ctx).forEach { scheduleOne(ctx, it) }
    }

    /** Test: 5 second baad launch, 25 second baad close (repeat nahi hota). */
    fun scheduleTest(ctx: Context, pkg: String) {
        val now = System.currentTimeMillis()
        setAt(ctx, now + 5_000, pending(ctx, TEST_LAUNCH_CODE, ACTION_LAUNCH, pkg, -1, false))
        setAt(ctx, now + 25_000, pending(ctx, TEST_CLOSE_CODE, ACTION_CLOSE, pkg, -1, false))
    }
}
