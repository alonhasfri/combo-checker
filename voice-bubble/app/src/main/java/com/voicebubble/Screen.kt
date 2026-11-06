package com.voicebubble

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/** Base for every screen: rebuilt on each return, so it always shows current data and theme. */
abstract class Screen : Activity() {
    abstract fun render()

    override fun onResume() {
        super.onResume()
        render()
    }

    protected val p get() = Ui.pal(this)
    protected fun dp(v: Int) = Ui.dp(this, v)

    protected fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    protected fun copy(s: String) =
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", s))
}
