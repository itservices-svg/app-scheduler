package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
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
import android.view.inputmethod.EditorInfo

/**
 * 1) Force stop: App Info screen kholta hai, "Force stop" dabata hai, phir confirm dialog mein "OK".
 *    (Settings English mein honi chahiye.)
 * 2) Unlock: lock screen par password field mein text set karta hai (ya number PIN pad dabata hai),
 *    phir Enter dabata hai, aur CHECK karta hai ke phone sach mein unlock hua ya nahi.
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

    private fun isLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    /**
     * Lock screen par secret (PIN/password) dalta hai.
     * onDone(true) = phone sach mein unlock ho gaya, onDone(false) = unlock nahi hua (wajah RunLog mein).
     */
    fun unlockWithPin(secret: String, onDone: (Boolean) -> Unit) {
        unlockRun++
        val myRun = unlockRun
        // Unlock screen WakeActivity kholti hai, swipe sirf backup hai
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

        // Kisi aur tareeqe se (ya user ne khud) pehle hi unlock ho gaya
        if (!isLocked()) {
            RunLog.add(this, "UNLOCK: phone pehle se unlocked hai")
            onDone(true)
            return
        }

        val digitsOnly = secret.isNotEmpty() && secret.all { it.isDigit() }

        // 1) Number PIN pad (sirf digits wale secret ke liye)
        if (digitsOnly && clickDigit(secret[0])) {
            RunLog.add(this, "UNLOCK: PIN pad mila, digits dab raha hun")
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
                RunLog.add(this, "UNLOCK: password field mili (${field.viewIdResourceName}), text daal diya")
                handler.postDelayed({
                    submit(field)
                    handler.postDelayed({ verifyAfterEnter(secret, myRun, onDone) }, 1500)
                }, 400)
                return
            }
        }

        if (attempt < 30) {
            // Unlock screen abhi nazar nahi aayi, thori der baad dobara (swipe backup)
            if (attempt == 1 || (attempt > 0 && attempt % 4 == 0)) swipeUp()
            handler.postDelayed({ unlockStep(secret, attempt + 1, myRun, onDone) }, 400)
        } else {
            RunLog.add(this, "UNLOCK FAIL: unlock screen ya password field nahi mili")
            onDone(false)
        }
    }

    /**
     * Enter dabane ke baad check: phone unlock hua? Nahi hua to
     * - field khali/gum hai: password submit ho chuka magar galat tha (dobara try nahi karte, taake
     *   galat attempts se phone lockout na ho)
     * - text abhi field mein hai: Enter register nahi hua, doosre tareeqe try karte hain.
     */
    private fun verifyAfterEnter(secret: String, myRun: Int, onDone: (Boolean) -> Unit) {
        if (myRun != unlockRun) return
        if (!isLocked()) {
            RunLog.add(this, "UNLOCK kamyab (keyboard Enter se)")
            onDone(true)
            return
        }
        val f = findPasswordField()
        val len = f?.text?.length ?: 0
        if (f == null || len == 0) {
            RunLog.add(this, "UNLOCK FAIL: Enter ke baad field khali/gum hai magar phone locked hai. Password galat save hua ho sakta hai")
            onDone(false)
            return
        }

        RunLog.add(this, "Enter register nahi hua (field mein $len characters hain), keyboard ka Enter button try kar raha hun")
        clickEnterButtons()
        handler.postDelayed({
            if (myRun != unlockRun) return@postDelayed
            if (!isLocked()) {
                RunLog.add(this, "UNLOCK kamyab (Enter button se)")
                onDone(true)
                return@postDelayed
            }
            val f2 = findPasswordField()
            if (f2 == null || (f2.text?.length ?: 0) == 0) {
                RunLog.add(this, "UNLOCK FAIL: Enter button ke baad bhi phone locked (password galat ho sakta hai)")
                onDone(false)
                return@postDelayed
            }
            if (typeViaInputMethod(f2, secret)) {
                handler.postDelayed({
                    if (myRun != unlockRun) return@postDelayed
                    val ok = !isLocked()
                    RunLog.add(this, if (ok) "UNLOCK kamyab (InputMethod se)" else "UNLOCK FAIL: teeno tareeqon ke baad bhi phone locked")
                    onDone(ok)
                }, 1500)
            } else {
                onDone(false)
            }
        }, 1500)
    }

    /** Android 13+: accessibility ka apna input connection, bilkul keyboard ki tarah type karta hai. */
    private fun typeViaInputMethod(field: AccessibilityNodeInfo, secret: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            RunLog.add(this, "InputMethod tareeqa Android 13+ par hi chalta hai")
            return false
        }
        return try {
            val clear = Bundle()
            clear.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clear)
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val ic = inputMethod?.currentInputConnection
            if (ic == null) {
                RunLog.add(this, "InputMethod connection nahi mila")
                false
            } else {
                ic.commitText(secret, 1, null)
                ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                RunLog.add(this, "InputMethod se password type kiya")
                true
            }
        } catch (e: Exception) {
            RunLog.add(this, "InputMethod error: ${e.message}")
            false
        }
    }

    private fun typeDigit(pin: String, idx: Int, attempt: Int, myRun: Int, onDone: (Boolean) -> Unit) {
        if (myRun != unlockRun) return
        if (idx >= pin.length) {
            handler.postDelayed({
                submit(null)
                handler.postDelayed({
                    if (myRun != unlockRun) return@postDelayed
                    val ok = !isLocked()
                    RunLog.add(this, if (ok) "UNLOCK kamyab (PIN pad se)" else "UNLOCK FAIL: PIN pad ke baad bhi phone locked")
                    onDone(ok)
                }, 1500)
            }, 300)
            return
        }
        if (clickDigit(pin[idx])) {
            handler.postDelayed({ typeDigit(pin, idx + 1, 0, myRun, onDone) }, 180)
        } else if (attempt < 8) {
            handler.postDelayed({ typeDigit(pin, idx, attempt + 1, myRun, onDone) }, 400)
        } else {
            RunLog.add(this, "UNLOCK FAIL: PIN pad beech mein gum ho gaya")
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

    /** Enter dabana: keyboard ka Enter action (Android 11+), warna "Enter" button. */
    private fun submit(field: AccessibilityNodeInfo?) {
        if (field != null && Build.VERSION.SDK_INT >= 30) {
            if (field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return
        }
        clickEnterButtons()
    }

    /** Keyboard ya lock screen ke Enter/Done/Go button ko dhoondh kar dabana. */
    private fun clickEnterButtons() {
        for (root in allRoots()) {
            for (n in root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/key_enter")) {
                if (clickNode(n)) return
            }
            for (label in listOf("Enter", "Done", "Go", "Next")) {
                for (n in root.findAccessibilityNodeInfosByText(label)) {
                    if (clickNode(n)) return
                }
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
