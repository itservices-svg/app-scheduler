package com.example.appscheduler

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Doosri app ko band karne ke liye: App Info screen kholta hai, "Force stop" dabata hai,
 * phir confirm dialog mein "OK" dabata hai. (Settings English mein honi chahiye.)
 */
class CloserService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CloserService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var step = 0 // 0 = idle, 1 = Force stop button ka intezar, 2 = confirm dialog ka intezar
    private var run = 0

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
