package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 1) Force stop: App Info screen kholta hai, "Force stop" dabata hai, phir confirm dialog mein "OK".
 *    (Settings English mein honi chahiye.)
 * 2) Unlock: lock screen par swipe-up karke ya to password field mein text set karta hai
 *    (letters + numbers wala password) ya number PIN pad ke digits dabata hai.
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

    // ------------------------- Unlock (PIN ya password) -------------------------

    /**
     * Lock screen ko swipe-up karke unlock screen kholta hai aur secret (PIN/password) dalta hai.
     * onDone(true) = secret dal diya gaya, onDone(false) = unlock screen ya field mila hi nahi.
     */
    fun unlockWithPin(secret: String, onDone: (Boolean) -> Unit) {
        unlockRun++
        val myRun = unlockRun
        // Unlock screen WakeActivity kholti hai, swipe sirf backup hai (neeche attempt 4 aur 9 par)
        handler.postDelayed({ unlockStep(secret, 0, myRun, onDone) }, 1200)
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

    private fun unlockStep(secret: String, attempt: Int, myRun: Int, onDone: (Boolean) -> Unit) {
        if (myRun != unlockRun) return

        val digitsOnly = secret.isNotEmpty() && secret.all { it.isDigit() }

        // 1) Number PIN pad (sirf digits wale secret ke liye)
        if (digitsOnly && clickDigit(secret[0])) {
            typeDigit(secret, 1, 0, myRun, onDone)
            return
        }

        // 2) Password field (letters/numbers wala password, ya PIN ki entry field)
        val field = findPasswordField()
        if (field != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, secret)
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                handler.postDelayed({
                    submit(field)
                    handler.postDelayed({ if (myRun == unlockRun) onDone(true) }, 1200)
                }, 400)
                return
            }
        }

        if (attempt < 14) {
            // Unlock screen abhi nazar nahi aayi, thori der baad dobara
            if (attempt == 4 || attempt == 9) swipeUp()
            handler.postDelayed({ unlockStep(secret, attempt + 1, myRun, onDone) }, 400)
        } else {
            onDone(false)
        }
    }

    private fun typeDigit(pin: String, idx: Int, attempt: Int, myRun: Int, onDone: (Boolean) -> Unit) {
        if (myRun != unlockRun) return
        if (idx >= pin.length) {
            handler.postDelayed({
                submit(null)
                handler.postDelayed({ if (myRun == unlockRun) onDone(true) }, 1200)
            }, 300)
            return
        }
        if (clickDigit(pin[idx])) {
            handler.postDelayed({ typeDigit(pin, idx + 1, 0, myRun, onDone) }, 180)
        } else if (attempt < 8) {
            handler.postDelayed({ typeDigit(pin, idx, attempt + 1, myRun, onDone) }, 400)
        } else {
            onDone(false) // PIN pad beech mein gum ho gaya
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

    private fun findPasswordField(): AccessibilityNodeInfo? {
        val roots = allRoots()
        for (root in roots) {
            for (id in listOf("com.android.systemui:id/passwordEntry", "com.android.systemui:id/pinEntry")) {
                val n = root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
                if (n != null) return n
            }
        }
        for (root in roots) {
            val n = firstEditable(root, 0)
            if (n != null) return n
        }
        return null
    }

    private fun firstEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 25) return null
        if (n.isEditable && n.isPassword) return n
        for (i in 0 until n.childCount) {
            val r = firstEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    /** Enter dabana: pehle keyboard ka Enter action, phir "Enter" button. */
    private fun submit(field: AccessibilityNodeInfo?) {
        if (field != null && Build.VERSION.SDK_INT >= 30) {
            if (field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return
        }
        for (root in allRoots()) {
            for (n in root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/key_enter")) {
                if (clickNode(n)) return
            }
            for (n in root.findAccessibilityNodeInfosByText("Enter")) {
                if (clickNode(n)) return
            }
        }
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
