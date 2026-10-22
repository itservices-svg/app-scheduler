package com.example.appscheduler

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Scheduler.EXTRA_PKG) ?: return
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val repeat = intent.getBooleanExtra(Scheduler.EXTRA_REPEAT, false)

        // Asli schedule se taaza settings, warna test ke liye intent ke extras
        val s = if (id > 0) Store.get(ctx, id) else null
        val mode = s?.closeMode ?: intent.getIntExtra(Scheduler.EXTRA_MODE, CLOSE_SOFT)
        val alarm = s?.alarm ?: (intent.getStringExtra(Scheduler.EXTRA_ALARM) ?: "")

        when (intent.action) {
            Scheduler.ACTION_LAUNCH -> launch(ctx.applicationContext, pkg, alarm)
            Scheduler.ACTION_CLOSE -> close(ctx.applicationContext, pkg, mode)
        }

        // Agle din ke liye dobara schedule
        if (repeat && s != null && s.enabled) {
            Scheduler.scheduleOne(ctx, s)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "appscheduler:wake"
            )
            wl.acquire(30_000)
        } catch (_: Exception) {
        }
    }

    private fun startTarget(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) {
                Toast.makeText(ctx, "App nahi mili: $pkg", Toast.LENGTH_LONG).show()
                return
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Launch fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showUnlockPrompt(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(ctx, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    private fun launch(ctx: Context, pkg: String, alarm: String) {
        wakeScreen(ctx)

        // Alarm pehle, taake unlock mein der ho to bhi bajne lage
        if (alarm.isNotEmpty()) AlarmSoundService.start(ctx, alarm)

        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pin = PinVault.load(ctx)
        val svc = CloserService.instance

        if (km.isKeyguardLocked && pin != null && svc != null) {
            // Phone lock hai aur PIN saved hai: PIN dal kar unlock karo, phir app kholo
            svc.unlockWithPin(pin) {
                startTarget(ctx, pkg)
                // Agar unlock nahi ho saka to user ke liye system ka unlock prompt
                if (km.isKeyguardLocked && Prefs.unlockPrompt(ctx)) showUnlockPrompt(ctx)
            }
            return
        }

        // App khol do (phone lock ho to bhi wo lock screen ke peeche khul jati hai)
        startTarget(ctx, pkg)

        // Lock ho aur user ne option on rakha ho to unlock prompt dikhao
        if (Prefs.unlockPrompt(ctx) && km.isKeyguardLocked) showUnlockPrompt(ctx)
    }

    private fun close(ctx: Context, pkg: String, mode: Int) {
        when (mode) {
            CLOSE_NONE -> {}
            CLOSE_SOFT -> {
                val pending = goAsync()
                Closer.soft(ctx, pkg) { pending.finish() }
            }
            else -> {
                val svc = CloserService.instance
                if (svc == null) {
                    Toast.makeText(
                        ctx, "Accessibility service band hai, Force stop nahi ho sakta", Toast.LENGTH_LONG
                    ).show()
                } else {
                    svc.forceStop(pkg)
                }
            }
        }
    }
}
