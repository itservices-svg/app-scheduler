package com.example.appscheduler

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Lock screen par screen on karke system ka unlock prompt dikhata hai (fingerprint/PIN/pattern).
 * Phone khud unlock nahi hota: user ko khud authenticate karna parta hai. Unlock hote hi
 * neeche khuli hui target app nazar aa jati hai.
 */
class WakeActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissCancelled() {
                finish()
            }

            override fun onDismissError() {
                finish()
            }
        })

        // Safety: 60 second baad khud band
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 60_000)
    }
}
