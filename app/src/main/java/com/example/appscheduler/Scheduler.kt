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
    const val EXTRA_MODE = "mode"
    const val EXTRA_ALARM = "alarm"
    const val EXTRA_AT = "at"

    private const val TEST_LAUNCH_CODE = 900001
    private const val TEST_CLOSE_CODE = 900002

    private fun pending(
        ctx: Context, code: Int, action: String, s: Schedule, repeat: Boolean, at: Long = 0L
    ): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_PKG, s.pkg)
            .putExtra(EXTRA_ID, s.id)
            .putExtra(EXTRA_REPEAT, repeat)
            .putExtra(EXTRA_MODE, s.closeMode)
            .putExtra(EXTRA_ALARM, s.alarm)
            .putExtra(EXTRA_AT, at)
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
        am.cancel(pending(ctx, s.id * 2, ACTION_LAUNCH, s, true))
        am.cancel(pending(ctx, s.id * 2 + 1, ACTION_CLOSE, s, true))
    }

    fun scheduleOne(ctx: Context, s: Schedule) {
        cancel(ctx, s)
        if (!s.enabled) return
        val startAt = nextTrigger(s.startH, s.startM)
        setAt(ctx, startAt, pending(ctx, s.id * 2, ACTION_LAUNCH, s, true, startAt))
        // Close optional hai
        if (s.closeMode != CLOSE_NONE) {
            val endAt = nextTrigger(s.endH, s.endM)
            setAt(ctx, endAt, pending(ctx, s.id * 2 + 1, ACTION_CLOSE, s, true, endAt))
            RunLog.add(ctx, "SET ${s.label}: launch ${RunLog.hms(startAt)}, close ${RunLog.hms(endAt)}")
        } else {
            RunLog.add(ctx, "SET ${s.label}: launch ${RunLog.hms(startAt)}, close nahi")
        }
    }

    fun scheduleAll(ctx: Context) {
        Store.all(ctx).forEach { scheduleOne(ctx, it) }
    }

    /** Test: launchSec baad launch (+ alarm), uske 20 second baad close (agar close on hai). Repeat nahi hota. */
    fun scheduleTest(ctx: Context, s: Schedule, launchSec: Int = 5) {
        val t = s.copy(id = -1)
        val now = System.currentTimeMillis()
        val launchAt = now + launchSec * 1000L
        setAt(ctx, launchAt, pending(ctx, TEST_LAUNCH_CODE, ACTION_LAUNCH, t, false, launchAt))
        if (s.closeMode != CLOSE_NONE) {
            val closeAt = now + (launchSec + 20) * 1000L
            setAt(ctx, closeAt, pending(ctx, TEST_CLOSE_CODE, ACTION_CLOSE, t, false, closeAt))
            RunLog.add(ctx, "TEST set: launch ${RunLog.hms(launchAt)}, close ${RunLog.hms(closeAt)}")
        } else {
            RunLog.add(ctx, "TEST set: launch ${RunLog.hms(launchAt)}, close nahi")
        }
    }
}
