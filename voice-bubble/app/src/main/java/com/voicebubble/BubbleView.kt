package com.voicebubble

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Bubble looks. Pick one in Settings → Bubble skin. */
object Skins {
    class Skin(val id: String, val title: String, val idle: Int, val idle2: Int? = null, val icon: Int = Color.WHITE,
               val shape: String = "circle", val glow: Int? = null)

    val all = listOf(
        Skin("classic", "Classic", Color.rgb(58, 91, 217)),
        Skin("midnight", "Midnight", Color.rgb(17, 21, 34), glow = Color.rgb(90, 110, 170)),
        Skin("sunset", "Sunset", Color.rgb(255, 122, 69), idle2 = Color.rgb(232, 62, 140)),
        Skin("mint", "Mint", Color.rgb(25, 179, 138)),
        Skin("heart", "Heart", Color.rgb(230, 20, 40), shape = "heart"),
        Skin("sixeyes", "Six Eyes", Color.rgb(11, 18, 48), icon = Color.rgb(127, 219, 255), glow = Color.rgb(127, 219, 255)),
        Skin("star", "Star", Color.rgb(255, 210, 63), icon = Color.rgb(60, 40, 0), shape = "star", glow = Color.rgb(255, 240, 160)),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]
}

/** The floating button: a microphone that turns red while listening. */
class BubbleView(context: Context) : View(context) {
    enum class State { IDLE, RECORDING, WORKING }

    var state = State.IDLE
        set(v) { field = v; alpha = if (v == State.IDLE) 0.85f else 1f; invalidate() }
    var level = 0f
    /** Caps lock on: a small "A" badge in the corner. */
    var caps = false
        set(v) { field = v; invalidate() }
    var skin: Skins.Skin = Skins.all[0]
        set(v) { field = v; invalidate() }
    /** Being moved: a highlighted outline. */
    var moving = false
        set(v) { field = v; invalidate() }
    var seconds = 0

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()
    private var spin = 0f
    private var shownLevel = 0f

    init {
        alpha = 0.85f
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for the glow blur
    }

    private fun shapePath(cx: Float, cy: Float, r: Float): Path {
        path.reset()
        when (skin.shape) {
            "heart" -> {
                val w = r * 1.05f
                path.moveTo(cx, cy + w * 0.9f)
                path.cubicTo(cx - w * 1.5f, cy - w * 0.1f, cx - w * 0.6f, cy - w * 1.15f, cx, cy - w * 0.45f)
                path.cubicTo(cx + w * 0.6f, cy - w * 1.15f, cx + w * 1.5f, cy - w * 0.1f, cx, cy + w * 0.9f)
                path.close()
            }
            "star" -> {
                for (i in 0 until 10) {
                    val rr = if (i % 2 == 0) r * 1.08f else r * 0.55f
                    val a = Math.toRadians((-90 + i * 36).toDouble())
                    val x = cx + (rr * cos(a)).toFloat()
                    val y = cy + (rr * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            else -> path.addCircle(cx, cy, r, Path.Direction.CW)
        }
        return path
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 8 * d

        fill.shader = null
        when (state) {
            State.IDLE -> {
                fill.color = skin.idle
                skin.idle2?.let { fill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, skin.idle, it, Shader.TileMode.CLAMP) }
            }
            State.RECORDING -> fill.color = if (skin.shape == "heart") Color.rgb(255, 40, 60) else Color.rgb(222, 48, 56)
            State.WORKING -> fill.color = Color.rgb(106, 63, 217)
        }

        if (state == State.RECORDING) {
            // Ring grows with your voice.
            shownLevel += (level - shownLevel) * 0.35f
            ring.color = Color.argb(110, 222, 48, 56)
            ring.strokeWidth = 3 * d + shownLevel * 6 * d
            c.drawCircle(cx, cy, r + 2 * d + shownLevel * 4 * d, ring)
        }
        val shape = shapePath(cx, cy, r)
        skin.glow?.let { g ->
            if (state == State.IDLE) {
                glowPaint.color = g
                glowPaint.strokeWidth = 3 * d
                glowPaint.maskFilter = BlurMaskFilter(4 * d, BlurMaskFilter.Blur.NORMAL)
                c.drawPath(shape, glowPaint)
            }
        }
        c.drawPath(shape, fill)
        if (moving) {
            ring.color = Color.WHITE
            ring.strokeWidth = 2.5f * d
            c.drawPath(shape, ring)
        }

        val ink = if (state == State.IDLE) skin.icon else Color.WHITE
        icon.color = ink
        iconFill.color = ink
        text.color = ink

        if (caps) {
            val br = r * 0.32f
            val bx = cx + r * 0.72f
            val by = cy - r * 0.72f
            iconFill.color = Color.WHITE
            c.drawCircle(bx, by, br, iconFill)
            text.textSize = br * 1.25f
            text.color = Color.rgb(58, 91, 217)
            c.drawText("A", bx, by - (text.ascent() + text.descent()) / 2, text)
            iconFill.color = ink
            text.color = ink
        }

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

        // Heart and star are drawn a bit higher/smaller so the icon sits in their middle.
        val s = r / 22f * (if (skin.shape == "circle") 1f else 0.85f)
        val oy = if (skin.shape == "heart") -r * 0.1f else 0f
        if (state == State.RECORDING) {
            // Stop square plus the running time: tap again to finish.
            val q = 6.5f * s
            c.drawRoundRect(RectF(cx - q, cy + oy - q - 4 * s, cx + q, cy + oy + q - 4 * s), 2.5f * s, 2.5f * s, iconFill)
            text.textSize = 7.5f * s
            c.drawText(String.format("%d:%02d", seconds / 60, seconds % 60), cx, cy + oy + 14 * s, text)
            postInvalidateOnAnimation()
            return
        }

        // Microphone icon.
        icon.strokeWidth = 2.4f * s
        val y = cy + oy
        c.drawRoundRect(RectF(cx - 5 * s, y - 11 * s, cx + 5 * s, y + 3 * s), 5 * s, 5 * s, iconFill)
        c.drawArc(RectF(cx - 9 * s, y - 7 * s, cx + 9 * s, y + 7.5f * s), 0f, 180f, false, icon)
        c.drawLine(cx, y + 7.5f * s, cx, y + 11.5f * s, icon)
    }
}
