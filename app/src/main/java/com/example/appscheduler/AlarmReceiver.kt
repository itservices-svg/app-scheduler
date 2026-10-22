package com.example.appscheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Scheduler.EXTRA_PKG) ?: return
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val repeat = intent.getBooleanExtra(Scheduler.EXTRA_REPEAT, false)

        when (intent.action) {
            Scheduler.ACTION_LAUNCH -> launch(ctx, pkg)
            Scheduler.ACTION_CLOSE -> close(ctx, pkg)
        }

        // Agle din ke liye dobara schedule
        if (repeat && id > 0) {
            Store.get(ctx, id)?.let { if (it.enabled) Scheduler.scheduleOne(ctx, it) }
        }
    }

    private fun launch(ctx: Context, pkg: String) {
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

    private fun close(ctx: Context, pkg: String) {
        val svc = CloserService.instance
        if (svc == null) {
            Toast.makeText(ctx, "Accessibility service band hai, app close nahi ho sakti", Toast.LENGTH_LONG).show()
            return
        }
        svc.forceStop(pkg)
    }
}
