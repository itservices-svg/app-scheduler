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
            Scheduler.ACTION_LAUNCH -> launch(ctx.applicationContext, pkg, alarm, mode, holdMsFor(ctx, s, mode))
            Scheduler.ACTION_CLOSE -> close(ctx.applicationContext, pkg, mode)
        }

        // Agle din ke liye dobara schedule
        if (repeat && s != null && s.enabled) {
            Scheduler.scheduleOne(ctx, s)
        }
    }

    /** Screen kitni der on rakhni hai (safety limit): launch se close time tak + keep-awake + buffer. */
    private fun holdMsFor(ctx: Context, s: Schedule?, mode: Int): Long {
        if (Prefs.neverLock(ctx)) return 8L * 60 * 60 * 1000
        val keep = Prefs.keepAwakeSec(ctx) * 1000L
        if (mode == CLOSE_NONE) return keep + 120_000L
        if (s == null) return keep + 150_000L // test
        var d = ((s.endH * 60 + s.endM) - (s.startH * 60 + s.startM) + 1440) % 1440
        if (d == 0) d = 1440
        return d * 60_000L + keep + 180_000L
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
            Prefs.setOpenedAt(ctx, System.currentTimeMillis())
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

    private fun lockPhone(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return CloserService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
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

        // Baad mein dobara lock karne ke liye yaad rakho ke phone pehle band/locked tha
        Prefs.setRelockPending(ctx, wasOff)

        // Screen on karo aur on rakho
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
                    if (ok) "UNLOCK ho gaya, ab app khol raha hun"
                    else "UNLOCK nahi hua (wajah upar log mein), phone abhi locked hai"
                )
                if (!ok) {
                    Toast.makeText(
                        ctx, "Phone unlock nahi hua, khud unlock karo. Wajah ke liye Log dekho", Toast.LENGTH_LONG
                    ).show()
                }
                startTarget(ctx, pkg)
                // Agar unlock nahi ho saka to user ke liye system ka unlock prompt
                if (km.isKeyguardLocked && Prefs.unlockPrompt(ctx)) showUnlockPrompt(ctx)
                afterOpen(ctx, mode)
            }
            return
        }

        // App khol do (phone lock ho to bhi wo lock screen ke peeche khul jati hai)
        startTarget(ctx, pkg)

        // Lock ho aur user ne option on rakha ho to unlock prompt dikhao
        if (Prefs.unlockPrompt(ctx) && km.isKeyguardLocked) showUnlockPrompt(ctx)
        afterOpen(ctx, mode)
    }

    /** Close nahi wale schedule: app khulte hi kaam khatam, ab sirf keep-awake ka intezar. */
    private fun afterOpen(ctx: Context, mode: Int) {
        if (mode == CLOSE_NONE) endHold(ctx)
    }

    private fun close(ctx: Context, pkg: String, mode: Int) {
        RunLog.add(ctx, "CLOSE $pkg | mode=$mode")
        when (mode) {
            CLOSE_NONE -> {}
            CLOSE_SOFT -> Closer.soft(ctx, pkg) { endHoldLater(ctx, 1500) }
            else -> {
                val svc = CloserService.instance
                if (svc == null) {
                    Toast.makeText(
                        ctx, "Accessibility service band hai, Force stop nahi ho sakta", Toast.LENGTH_LONG
                    ).show()
                    RunLog.add(ctx, "Force stop FAIL: accessibility band")
                    endHoldLater(ctx, 2000)
                } else {
                    svc.forceStop(pkg)
                    endHoldLater(ctx, 9000)
                }
            }
        }
    }

    private fun endHoldLater(ctx: Context, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ endHold(ctx) }, delayMs)
    }

    /**
     * Kaam khatam: phone ko app khulne ke keep-awake seconds tak lock/screen off nahi hone dena.
     * "Kabhi lock na ho" on ho to screen on hi rehti hai (app mein "Screen on band" dabao ya power button).
     * Warna us waqt ke baad wake lock hata diya jata hai aur (agar setting on ho aur phone pehle
     * locked tha) phone khud lock kar diya jata hai.
     */
    private fun endHold(ctx: Context) {
        val pending = Prefs.relockPending(ctx)
        if (Prefs.neverLock(ctx)) {
            Prefs.setRelockPending(ctx, false)
            RunLog.add(ctx, "Kabhi lock na ho: screen on rakhi hai (band karne ke liye app mein 'Screen on band')")
            return
        }
        val remaining = (Prefs.openedAt(ctx) + Prefs.keepAwakeSec(ctx) * 1000L - System.currentTimeMillis())
            .coerceAtLeast(0L)
        if (remaining > 0) RunLog.add(ctx, "Phone ${remaining / 1000}s aur lock nahi hoga")
        Handler(Looper.getMainLooper()).postDelayed({
            ScreenKeeper.release()
            Prefs.setRelockPending(ctx, false)
            if (pending && Prefs.relock(ctx)) {
                RunLog.add(ctx, "Phone dobara lock kiya: ${lockPhone()}")
            } else {
                RunLog.add(ctx, "Screen on rakhne ka lock hata diya (screen apne timeout par off hogi)")
            }
        }, remaining)
    }
}
