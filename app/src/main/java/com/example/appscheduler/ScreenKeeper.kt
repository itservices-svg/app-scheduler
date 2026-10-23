package com.example.appscheduler

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** Screen on rakhta hai (wake lock) taake phone beech mein dobara lock na ho. */
object ScreenKeeper {
    private var wl: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    /** maxMs ek safety timeout hai, us ke baad wake lock khud khatam ho jata hai. */
    @Suppress("DEPRECATION")
    fun hold(ctx: Context, maxMs: Long) {
        release()
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val l = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "appscheduler:screen"
            )
            l.setReferenceCounted(false)
            l.acquire(maxMs)
            wl = l
        } catch (_: Exception) {
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            wl?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wl = null
    }

    fun releaseSoon(delayMs: Long) {
        handler.postDelayed({ release() }, delayMs)
    }
}
