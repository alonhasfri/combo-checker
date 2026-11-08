package com.voicebubble

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Tells when an on-screen keyboard (any keyboard, e.g. Gboard) is open, and where its top edge is.
 *
 * Three signals, any of them counts:
 *  1. The autotype version's accessibility service sees the keyboard window directly (most reliable).
 *  2. Android's "keyboard visible" flag on an invisible 1-pixel strip we keep at the screen edge.
 *  3. That strip getting shorter because the keyboard pushed it up.
 */
class KeyboardWatcher(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onChange: (open: Boolean, top: Int?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var strip: View? = null
    private var maxHeight = 0
    private var lastWidth = 0
    private var open = false
    private var top: Int? = null
    private var closedCount = 0

    private val poll = object : Runnable {
        override fun run() {
            evaluate()
            // With the accessibility service, Android tells us right away (see poke), so checks can be rarer.
            main.postDelayed(this, if (InsertService.isOn) 250L else 120L)
        }
    }

    fun start() {
        val v = View(ctx)
        val lp = WindowManager.LayoutParams(
            1, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE + ALT_FOCUSABLE_IM together tell Android this window works with the keyboard,
            // so the keyboard resizes it and reports itself to it. Without ALT_FOCUSABLE_IM it is
            // treated as a window that never uses a keyboard and is told nothing.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Let the keyboard resize this window, so its height shows where the keyboard starts.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // Fully transparent: Android lets touches pass through it to the app underneath.
            alpha = 0f
        }
        v.setOnApplyWindowInsetsListener { view, insets -> main.post { evaluate() }; view.onApplyWindowInsets(insets) }
        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> main.post { evaluate() } }
        runCatching { wm.addView(v, lp) }
        strip = v
        main.post(poll)
    }

    /** The keyboard window just opened or closed (from the accessibility service): check now. */
    fun poke() {
        main.post { evaluate() }
        // Android may still be animating the keyboard: look again shortly after.
        main.postDelayed({ evaluate() }, 60)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
    }

    private fun evaluate() {
        val v = strip ?: return
        var nowOpen = false
        var nowTop: Int? = null

        val fromService = InsertService.instance?.let { runCatching { it.keyboardTop() }.getOrNull() }
        if (fromService != null) {
            nowOpen = fromService >= 0
            if (nowOpen) nowTop = fromService
        } else if (v.height > 0) {
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val bottom = loc[1] + v.height

            if (Build.VERSION.SDK_INT >= 30) {
                val ins = v.rootWindowInsets
                if (ins != null) {
                    val imeBottom = ins.getInsets(WindowInsets.Type.ime()).bottom
                    if (ins.isVisible(WindowInsets.Type.ime()) || imeBottom > 0) {
                        nowOpen = true
                        if (imeBottom > 0) nowTop = bottom - imeBottom
                    }
                }
            }

            // Rotation changes the full height; start measuring again.
            val w = ctx.resources.displayMetrics.widthPixels
            if (w != lastWidth) { lastWidth = w; maxHeight = 0 }
            if (v.height > maxHeight) maxHeight = v.height
            if (v.height < maxHeight * 0.82) {
                nowOpen = true
                if (nowTop == null) nowTop = bottom
            }
        }

        // Open right away; close only after it stays closed a moment (switching text boxes flickers).
        if (nowOpen) closedCount = 0 else closedCount++
        val report = nowOpen || (open && closedCount < 2 && !InsertService.isOn)
        val reportTop = if (nowOpen) nowTop else top
        if (report != open || reportTop != top) {
            open = report
            top = if (report) reportTop else null
            onChange(open, top)
        }
    }
}
