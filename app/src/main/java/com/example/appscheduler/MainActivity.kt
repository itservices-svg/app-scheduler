package com.example.appscheduler

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private class Draft(val label: String, val pkg: String) {
        var sh = 12
        var sm = 0
        var eh = 12
        var em = 5
        var closeMode = CLOSE_SOFT
        var alarm = ""
    }

    private var draft: Draft? = null
    private val reqRingtone = 77

    private lateinit var status: TextView
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(12), dp(16), dp(12))

        status = TextView(this)
        status.textSize = 14f
        root.addView(status)

        // Permission buttons
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(smallButton("Exact alarm") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        })
        row1.addView(smallButton("Overlay") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(smallButton("Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        row2.addView(smallButton("Battery") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(row2)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.addView(smallButton("Alarm stop") { AlarmSoundService.stop(this) })
        root.addView(row3)

        val unlock = Switch(this)
        unlock.text = "Phone lock ho to unlock prompt dikhao"
        unlock.isChecked = Prefs.unlockPrompt(this)
        unlock.setOnCheckedChangeListener { _, on -> Prefs.setUnlockPrompt(this, on) }
        unlock.setPadding(0, dp(8), 0, dp(4))
        root.addView(unlock)

        val add = Button(this)
        add.text = "+ Naya schedule"
        add.setOnClickListener { pickApp() }
        root.addView(add)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll)

        setContentView(root)

        // Android 13+: alarm notification dikhane ke liye
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun mark(ok: Boolean) = if (ok) "OK" else "NAHI"

    private fun refresh() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val overlay = Settings.canDrawOverlays(this)
        val a11y = CloserService.instance != null
        status.text = "Exact alarm: ${mark(exact)}   Overlay: ${mark(overlay)}   Accessibility: ${mark(a11y)}"

        list.removeAllViews()
        val items = Store.all(this)
        if (items.isEmpty()) {
            val t = TextView(this)
            t.text = "Abhi koi schedule nahi. Upar \"+ Naya schedule\" dabao."
            t.setPadding(0, dp(16), 0, 0)
            list.addView(t)
        }
        for (s in items) list.addView(rowFor(s))
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun rowFor(s: Schedule): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(14), 0, dp(6))

        val title = TextView(this)
        title.text = s.label
        title.textSize = 18f
        box.addView(title)

        val endTime = "${two(s.endH)}:${two(s.endM)}"
        val closeTxt = when (s.closeMode) {
            CLOSE_NONE -> "Close: nahi"
            CLOSE_SOFT -> "Close $endTime (soft)"
            else -> "Close $endTime (force stop)"
        }
        val alarmTxt = when (s.alarm) {
            "" -> "Alarm: nahi"
            "default" -> "Alarm: default"
            else -> "Alarm: custom sound"
        }
        val time = TextView(this)
        time.text = "Launch ${two(s.startH)}:${two(s.startM)}  ->  $closeTxt\n$alarmTxt"
        box.addView(time)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL

        val sw = Switch(this)
        sw.isChecked = s.enabled
        sw.setOnCheckedChangeListener { _, on ->
            val u = s.copy(enabled = on)
            Store.update(this@MainActivity, u)
            Scheduler.scheduleOne(this@MainActivity, u)
        }
        actions.addView(sw)

        actions.addView(smallButton("Test") {
            Scheduler.scheduleTest(this, s)
            val msg = if (s.closeMode == CLOSE_NONE) "5 sec mein launch" else "5 sec mein launch, 25 sec mein close"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        })
        actions.addView(smallButton("Delete") {
            Scheduler.cancel(this, s)
            Store.remove(this, s.id)
            refresh()
        })
        box.addView(actions)
        return box
    }

    // ---- Naya schedule banane ka flow: app -> launch time -> close mode -> (close time) -> alarm ----

    private fun pickApp() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        AlertDialog.Builder(this)
            .setTitle("App chuno")
            .setItems(apps.map { it.first }.toTypedArray()) { _, which ->
                val (label, pkg) = apps[which]
                draft = Draft(label, pkg)
                pickStart()
            }
            .show()
    }

    private fun pickStart() {
        val d = draft ?: return
        val dlg = TimePickerDialog(this, { _, h, m ->
            d.sh = h
            d.sm = m
            pickCloseMode()
        }, d.sh, d.sm, true)
        dlg.setTitle("Launch time (kholne ka waqt)")
        dlg.setOnCancelListener { draft = null }
        dlg.show()
    }

    private fun pickCloseMode() {
        val d = draft ?: return
        val items = arrayOf(
            "Close nahi karna",
            "Soft close (Home + background kill) - recommended",
            "Force stop (App Info screen dikhegi)"
        )
        AlertDialog.Builder(this)
            .setTitle("App ko band kaise karna hai?")
            .setItems(items) { _, which ->
                d.closeMode = which
                if (which == CLOSE_NONE) pickAlarm() else pickEnd()
            }
            .setOnCancelListener { draft = null }
            .show()
    }

    private fun pickEnd() {
        val d = draft ?: return
        val total = (d.sh * 60 + d.sm + 5) % (24 * 60)
        d.eh = total / 60
        d.em = total % 60
        val dlg = TimePickerDialog(this, { _, h, m ->
            d.eh = h
            d.em = m
            pickAlarm()
        }, d.eh, d.em, true)
        dlg.setTitle("Close time (band karne ka waqt)")
        dlg.setOnCancelListener { draft = null }
        dlg.show()
    }

    private fun pickAlarm() {
        val d = draft ?: return
        val items = arrayOf("Alarm nahi", "Default alarm (beep)", "Phone ka alarm sound chuno...")
        AlertDialog.Builder(this)
            .setTitle("App khulte hi alarm bajana hai?")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        d.alarm = ""
                        saveDraft()
                    }
                    1 -> {
                        d.alarm = "default"
                        saveDraft()
                    }
                    else -> {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound chuno")
                        startActivityForResult(i, reqRingtone)
                    }
                }
            }
            .setOnCancelListener { draft = null }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqRingtone) return
        val d = draft ?: return
        if (resultCode == RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            d.alarm = uri?.toString() ?: "default"
            saveDraft()
        } else {
            pickAlarm()
        }
    }

    private fun saveDraft() {
        val d = draft ?: return
        draft = null
        val s = Schedule(
            Store.nextId(this), d.pkg, d.label, d.sh, d.sm, d.eh, d.em, true, d.closeMode, d.alarm
        )
        Store.add(this, s)
        Scheduler.scheduleOne(this, s)
        refresh()
    }
}
