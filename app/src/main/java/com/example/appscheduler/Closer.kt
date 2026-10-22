package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object Closer {

    /** Home screen par jana (app "minimize" hoti hai). */
    fun goHome(ctx: Context) {
        val svc = CloserService.instance
        if (svc != null) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            try {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Soft close: pehle Home par jaata hai (app background mein chali jati hai),
     * phir system se app ke background processes khatam karwata hai.
     * App Info screen ya dialog nahi dikhta. Agar app kisi foreground service ki wajah se
     * chal rahi ho to ye usse band nahi kar sakta (tab Force stop mode use karo).
     */
    fun soft(ctx: Context, pkg: String, done: () -> Unit) {
        goHome(ctx)
        val h = Handler(Looper.getMainLooper())
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        h.postDelayed({ am.killBackgroundProcesses(pkg) }, 1500)
        h.postDelayed({
            am.killBackgroundProcesses(pkg)
            done()
        }, 3500)
    }
}
