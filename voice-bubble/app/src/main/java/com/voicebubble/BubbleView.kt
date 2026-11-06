package com.voicebubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** The floating round button: a microphone that turns red while listening. */
class BubbleView(context: Context) : View(context) {
    enum class State { IDLE, RECORDING, WORKING }

    var state = State.IDLE
        set(v) { field = v; alpha = if (v == State.IDLE) 0.75f else 1f; invalidate() }
    var level = 0f
    var seconds = 0

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private var spin = 0f
    private var shownLevel = 0f

    init { alpha = 0.75f }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 8 * d

        when (state) {
            State.IDLE -> fill.color = Color.rgb(32, 44, 64)
            State.RECORDING -> fill.color = Color.rgb(222, 48, 56)
            State.WORKING -> fill.color = Color.rgb(36, 110, 220)
        }

        if (state == State.RECORDING) {
            // Ring grows with your voice.
            shownLevel += (level - shownLevel) * 0.35f
            ring.color = Color.argb(110, 222, 48, 56)
            ring.strokeWidth = 3 * d + shownLevel * 6 * d
            c.drawCircle(cx, cy, r + 2 * d + shownLevel * 4 * d, ring)
        }
        c.drawCircle(cx, cy, r, fill)

        if (state == State.WORKING) {
            ring.color = Color.WHITE
            ring.strokeWidth = 3 * d
            ring.strokeCap = Paint.Cap.ROUND
            val a = r * 0.55f
            c.drawArc(RectF(cx - a, cy - a, cx + a, cy + a), spin, 100f, false, ring)
            spin = (spin + 9f) % 360f
            postInvalidateOnAnimation()
            return
        }

        val s = r / 22f
        if (state == State.RECORDING) {
            // Stop square plus the running time: tap again to finish.
            val q = 6.5f * s
            c.drawRoundRect(RectF(cx - q, cy - q - 4 * s, cx + q, cy + q - 4 * s), 2.5f * s, 2.5f * s, iconFill)
            text.textSize = 7.5f * s
            c.drawText(String.format("%d:%02d", seconds / 60, seconds % 60), cx, cy + 14 * s, text)
            postInvalidateOnAnimation()
            return
        }

        // Microphone icon.
        icon.strokeWidth = 2.4f * s
        c.drawRoundRect(RectF(cx - 5 * s, cy - 11 * s, cx + 5 * s, cy + 3 * s), 5 * s, 5 * s, iconFill)
        c.drawArc(RectF(cx - 9 * s, cy - 7 * s, cx + 9 * s, cy + 7.5f * s), 0f, 180f, false, icon)
        c.drawLine(cx, cy + 7.5f * s, cx, cy + 11.5f * s, icon)
    }
}
