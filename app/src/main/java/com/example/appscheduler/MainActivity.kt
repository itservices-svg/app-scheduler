package com.example.appscheduler

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
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

        val time = TextView(this)
        time.text = "Launch ${two(s.startH)}:${two(s.startM)}  ->  Close ${two(s.endH)}:${two(s.endM)}"
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
            Scheduler.scheduleTest(this, s.pkg)
            Toast.makeText(this, "5 sec mein launch, 25 sec mein close", Toast.LENGTH_LONG).show()
        })
        actions.addView(smallButton("Delete") {
            Scheduler.cancel(this, s)
            Store.remove(this, s.id)
            refresh()
        })
        box.addView(actions)
        return box
    }

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
                pickTimes(label, pkg)
            }
            .show()
    }

    private fun pickTimes(label: String, pkg: String) {
        val startDlg = TimePickerDialog(this, { _, sh, sm ->
            val endDlg = TimePickerDialog(this, { _, eh, em ->
                val s = Schedule(Store.nextId(this), pkg, label, sh, sm, eh, em, true)
                Store.add(this, s)
                Scheduler.scheduleOne(this, s)
                refresh()
            }, sh, (sm + 5) % 60, true)
            endDlg.setTitle("Close time (band karne ka waqt)")
            endDlg.show()
        }, 12, 0, true)
        startDlg.setTitle("Launch time (kholne ka waqt)")
        startDlg.show()
    }
}
