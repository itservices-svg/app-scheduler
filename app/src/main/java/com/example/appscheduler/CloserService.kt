package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 1) Force stop: App Info screen kholta hai, "Force stop" dabata hai, phir confirm dialog mein "OK".
 *    (Settings English mein honi chahiye.)
 * 2) PIN unlock: lock screen par swipe-up karke PIN pad ke digits dabata hai.
 */
class CloserService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CloserService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var step = 0 // 0 = idle, 1 = Force stop button ka intezar, 2 = confirm dialog ka intezar, 3 = finish ka intezar
    private var run = 0
    private var unlockRun = 0

    private val forceStopLabels = listOf("Force stop", "FORCE STOP", "Force Stop")
    private val confirmLabels = listOf("OK", "Ok", "Force stop", "FORCE STOP")

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ------------------------- Force stop -------------------------

    fun forceStop(pkg: String) {
        run++
        val myRun = run
        step = 1
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            step = 0
            return
        }
        // Timeout: 8 second mein kaam na ho to wapas home
        handler.postDelayed({ if (myRun == run) finish() }, 8000)
    }

    private fun finish() {
        if (step == 0) return
        step = 0
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (step == 0) return
        val root = rootInActiveWindow ?: return

        if (step == 1) {
            for (label in forceStopLabels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                val node = nodes.firstOrNull() ?: continue
                if (!node.isEnabled) { // app pehle se band hai
                    finish()
                    return
                }
                if (clickNode(node)) {
                    step = 2
                    return
                }
            }
        } else if (step == 2) {
            for (label in confirmLabels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                val node = nodes.firstOrNull() ?: continue
                if (clickNode(node)) {
                    val myRun = run
                    handler.postDelayed({ if (myRun == run) finish() }, 700)
                    step = 3 // click ho gaya, sirf finish ka intezar
                    return
                }
            }
        }
    }

    // ------------------------- PIN unlock -------------------------

    /**
     * Lock screen ko swipe-up karke PIN pad kholta hai aur PIN ke digits dabata hai.
     * Kaam khatam hone par (kamyab ho ya nakam) onDone chalta hai.
     */
    fun unlockWithPin(pin: String, onDone: () -> Unit) {
        unlockRun++
        val myRun = unlockRun
        swipeUp()
        handler.postDelayed({ typeDigit(pin, 0, 0, myRun, onDone) }, 900)
    }

    private fun swipeUp() {
        try {
            val dm = resources.displayMetrics
            val p = Path()
            p.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.85f)
            p.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.25f)
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, 300))
                .build()
            dispatchGesture(g, null, null)
        } catch (_: Exception) {
        }
    }

    private fun typeDigit(pin: String, idx: Int, attempt: Int, myRun: Int, onDone: () -> Unit) {
        if (myRun != unlockRun) return
        if (idx >= pin.length) {
            handler.postDelayed({
                pressEnter()
                handler.postDelayed({ if (myRun == unlockRun) onDone() }, 1200)
            }, 300)
            return
        }
        if (clickDigit(pin[idx])) {
            handler.postDelayed({ typeDigit(pin, idx + 1, 0, myRun, onDone) }, 180)
        } else if (attempt < 8) {
            // PIN pad abhi nazar nahi aaya, thori der baad dobara
            if (attempt == 3) swipeUp()
            handler.postDelayed({ typeDigit(pin, idx, attempt + 1, myRun, onDone) }, 400)
        } else {
            onDone() // PIN pad mila hi nahi
        }
    }

    private fun allRoots(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) w.root?.let { out.add(it) }
        } catch (_: Exception) {
        }
        rootInActiveWindow?.let { out.add(it) }
        return out
    }

    private fun clickDigit(d: Char): Boolean {
        val s = d.toString()
        for (root in allRoots()) {
            for (n in root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/key$d")) {
                if (clickNode(n)) return true
            }
            for (n in root.findAccessibilityNodeInfosByText(s)) {
                val t = n.text?.toString()
                val cd = n.contentDescription?.toString()
                if ((t == s || cd == s) && clickNode(n)) return true
            }
        }
        return false
    }

    private fun pressEnter() {
        for (root in allRoots()) {
            for (n in root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/key_enter")) {
                if (clickNode(n)) return
            }
            for (n in root.findAccessibilityNodeInfosByText("Enter")) {
                if (clickNode(n)) return
            }
        }
    }

    private fun clickNode(n: AccessibilityNodeInfo?): Boolean {
        var cur = n
        while (cur != null) {
            if (cur.isClickable && cur.isEnabled) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        return false
    }
}
