package com.voicebubble

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Invisible: starts the bubble (from the Quick Settings tile) and closes. */
class StartBubbleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Setup.readyToStart(this)) {
            startForegroundService(Intent(this, BubbleService::class.java))
        } else {
            // Something still needs setting up: show Home with the checklist.
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
