package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Scheduler.EXTRA_PKG) ?: return
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val repeat = intent.getBooleanExtra(Scheduler.EXTRA_REPEAT, false)

        // Diagnostic: alarm kab aana tha aur kab aaya
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (at > 0) {
            val now = System.currentTimeMillis()
            val kind = if (intent.action == Scheduler.ACTION_LAUNCH) "launch" else "close"
            RunLog.add(ctx, "ALARM $kind: due ${RunLog.hms(at)}, aaya ${RunLog.hms(now)} (${(now - at) / 1000}s late)")
        }

        // Asli schedule se taaza settings, warna test ke liye intent ke extras
        val s = if (id > 0) Store.get(ctx, id) else null
        val mode = s?.closeMode ?: intent.getIntExtra(Scheduler.EXTRA_MODE, CLOSE_SOFT)
        val alarm = s?.alarm ?: (intent.getStringExtra(Scheduler.EXTRA_ALARM) ?: "")

        when (intent.action) {
            Scheduler.ACTION_LAUNCH -> launch(ctx.applicationContext, pkg, alarm, mode, holdMsFor(s, mode))
            Scheduler.ACTION_CLOSE -> close(ctx.applicationContext, pkg, mode)
        }

        // Agle din ke liye dobara schedule
        if (repeat && s != null && s.enabled) {
            Scheduler.scheduleOne(ctx, s)
        }
    }

    /** Screen kitni der on rakhni hai (safety limit): launch se close time tak + 2 minute. */
    private fun holdMsFor(s: Schedule?, mode: Int): Long {
        if (mode == CLOSE_NONE) return 60_000L
        if (s == null) return 90_000L // test
        var d = ((s.endH * 60 + s.endM) - (s.startH * 60 + s.startM) + 1440) % 1440
        if (d == 0) d = 1440
        return d * 60_000L + 120_000L
    }

    private fun startTarget(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) {
                Toast.makeText(ctx, "App nahi mili: $pkg", Toast.LENGTH_LONG).show()
                RunLog.add(ctx, "App nahi mili: $pkg")
                return
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
            RunLog.add(ctx, "App kholi: $pkg")
        } catch (e: Exception) {
            Toast.makeText(ctx, "Launch fail: ${e.message}", Toast.LENGTH_LONG).show()
            RunLog.add(ctx, "Launch FAIL: ${e.message}")
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

    private fun launch(ctx: Context, pkg: String, alarm: String, mode: Int, holdMs: Long) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val wasOff = !pm.isInteractive || km.isKeyguardLocked
        val pin = PinVault.load(ctx)
        val svc = CloserService.instance

        RunLog.add(
            ctx,
            "LAUNCH $pkg | locked=${km.isKeyguardLocked} screenOn=${pm.isInteractive} " +
                "passwordSaved=${pin != null} accessibility=${svc != null}"
        )

        // Screen on karo aur close tak on rakho
        ScreenKeeper.hold(ctx, holdMs)

        // Alarm pehle, taake unlock mein der ho to bhi bajne lage
        if (alarm.isNotEmpty()) AlarmSoundService.start(ctx, alarm)

        if (km.isKeyguardLocked && pin == null) {
            Toast.makeText(ctx, "Unlock PIN/Password save nahi hai (app mein button dabao)", Toast.LENGTH_LONG).show()
        } else if (km.isKeyguardLocked && svc == null) {
            Toast.makeText(ctx, "Accessibility service band hai, auto-unlock nahi ho sakta", Toast.LENGTH_LONG).show()
        }

        if (km.isKeyguardLocked && pin != null && svc != null) {
            // Pehle system ka unlock screen (password/PIN box) kholo, phir service usme secret daale
            showUnlockPrompt(ctx)
            svc.unlockWithPin(pin) { ok ->
                RunLog.add(
                    ctx,
                    if (ok) "UNLOCK password dala gaya (ab locked=${km.isKeyguardLocked})"
                    else "UNLOCK FAIL: password field ya PIN pad nahi mila"
                )
                if (!ok) {
                    Toast.makeText(
                        ctx, "Unlock screen ya password field nahi mili, khud unlock karo", Toast.LENGTH_LONG
                    ).show()
                }
                startTarget(ctx, pkg)
                // Agar unlock nahi ho saka to user ke liye system ka unlock prompt
                if (km.isKeyguardLocked && Prefs.unlockPrompt(ctx)) showUnlockPrompt(ctx)
                afterOpen(ctx, km, mode, wasOff)
            }
            return
        }

        // App khol do (phone lock ho to bhi wo lock screen ke peeche khul jati hai)
        startTarget(ctx, pkg)

        // Lock ho aur user ne option on rakha ho to unlock prompt dikhao
        if (Prefs.unlockPrompt(ctx) && km.isKeyguardLocked) showUnlockPrompt(ctx)
        afterOpen(ctx, km, mode, wasOff)
    }

    /**
     * Close wale schedule: screen close tak on rehti hai (close par band hoti hai).
     * Close nahi wale schedule: app khulne ke 5 second baad screen off (sirf agar phone pehle
     * band/locked tha, taake tumhare istemal ke beech phone lock na ho).
     */
    private fun afterOpen(ctx: Context, km: KeyguardManager, mode: Int, wasOff: Boolean) {
        if (mode != CLOSE_NONE) return
        Handler(Looper.getMainLooper()).postDelayed({
            val stillLocked = km.isKeyguardLocked
            ScreenKeeper.release()
            if (wasOff && !stillLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                CloserService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                RunLog.add(ctx, "5 sec baad screen off kar di")
            } else {
                RunLog.add(ctx, "5 sec baad screen on rakhne ka lock hata diya")
            }
        }, 5000)
    }

    private fun close(ctx: Context, pkg: String, mode: Int) {
        RunLog.add(ctx, "CLOSE $pkg | mode=$mode")
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
                    RunLog.add(ctx, "Force stop FAIL: accessibility band")
                } else {
                    svc.forceStop(pkg)
                }
            }
        }
        // Ab screen ko apne time par off hone do
        if (mode != CLOSE_NONE) ScreenKeeper.releaseSoon(6000)
    }
}
