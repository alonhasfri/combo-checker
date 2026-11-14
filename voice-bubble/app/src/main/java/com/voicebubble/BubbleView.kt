package com.voicebubble

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Bubble looks. Pick one in Settings → Bubble skin. */
object Skins {
    /** [pattern]: something drawn on top ("eye" replaces the microphone, "galaxy" adds stars, "rainbow" colours it). */
    class Skin(val id: String, val title: String, val idle: Int, val idle2: Int? = null, val icon: Int = Color.WHITE,
               val shape: String = "circle", val glow: Int? = null, val pattern: String = "",
               /** Your own skins: an uploaded picture, or the AI's drawing (and whether it keeps the microphone). */
               val image: android.graphics.Bitmap? = null, val art: org.json.JSONObject? = null,
               val showMic: Boolean = true, val custom: Boolean = false)

    val all = listOf(
        Skin("classic", "Classic", Color.rgb(58, 91, 217)),
        Skin("midnight", "Midnight", Color.rgb(17, 21, 34), glow = Color.rgb(90, 110, 170)),
        Skin("sunset", "Sunset", Color.rgb(255, 122, 69), idle2 = Color.rgb(232, 62, 140)),
        Skin("mint", "Mint", Color.rgb(25, 179, 138)),
        Skin("heart", "Heart", Color.rgb(230, 20, 40), shape = "heart"),
        Skin("gojo", "Six Eyes", Color.rgb(8, 12, 30), glow = Color.rgb(120, 215, 255), pattern = "eye"),
        // The old "Six Eyes" look (kept under its old id, so it stays picked for you).
        Skin("sixeyes", "Neon blue", Color.rgb(11, 18, 48), icon = Color.rgb(127, 219, 255), glow = Color.rgb(127, 219, 255)),
        Skin("star", "Star", Color.rgb(255, 210, 63), icon = Color.rgb(60, 40, 0), shape = "star", glow = Color.rgb(255, 240, 160)),
        Skin("galaxy", "Galaxy", Color.rgb(70, 30, 140), idle2 = Color.rgb(12, 10, 40), glow = Color.rgb(170, 120, 255), pattern = "galaxy"),
        Skin("rainbow", "Rainbow", Color.rgb(255, 80, 80), pattern = "rainbow"),
        Skin("lava", "Lava", Color.rgb(255, 140, 0), idle2 = Color.rgb(190, 10, 30), glow = Color.rgb(255, 120, 40)),
        Skin("ocean", "Ocean", Color.rgb(0, 200, 230), idle2 = Color.rgb(20, 60, 170)),
        Skin("bubblegum", "Bubblegum", Color.rgb(255, 110, 185), idle2 = Color.rgb(255, 175, 225)),
        Skin("gold", "Gold", Color.rgb(255, 222, 110), idle2 = Color.rgb(190, 135, 20), icon = Color.rgb(70, 45, 0), glow = Color.rgb(255, 230, 140)),
        Skin("matrix", "Matrix", Color.rgb(4, 10, 6), icon = Color.rgb(60, 255, 120), glow = Color.rgb(60, 255, 120)),
        Skin("ghost", "Ghost", Color.rgb(248, 249, 252), icon = Color.rgb(40, 44, 60), glow = Color.WHITE),
        Skin("void", "Void", Color.rgb(0, 0, 0), idle2 = Color.rgb(60, 20, 110), icon = Color.rgb(200, 170, 255), glow = Color.rgb(150, 90, 255)),
        Skin("cursed", "Cursed", Color.rgb(25, 0, 4), idle2 = Color.rgb(140, 0, 20), icon = Color.rgb(255, 90, 90), glow = Color.rgb(255, 40, 60)),
        Skin("soul", "Red soul", Color.rgb(255, 0, 20), shape = "heart", glow = Color.rgb(255, 60, 60)),
        Skin("diamond", "Diamond", Color.rgb(200, 245, 255), idle2 = Color.rgb(90, 200, 240), icon = Color.rgb(10, 50, 90), shape = "diamond", glow = Color.WHITE),
        Skin("honey", "Honey", Color.rgb(255, 190, 20), idle2 = Color.rgb(230, 140, 0), icon = Color.rgb(70, 40, 0), shape = "hexagon"),
        // Jujutsu Kaisen
        Skin("hollowpurple", "Hollow Purple", Color.rgb(120, 40, 200), idle2 = Color.rgb(30, 0, 60), glow = Color.rgb(200, 120, 255), pattern = "hollowpurple"),
        Skin("infinity", "Infinity", Color.rgb(170, 230, 255), idle2 = Color.rgb(40, 120, 220), glow = Color.rgb(150, 220, 255), pattern = "infinity"),
        Skin("domain", "Domain Expansion", Color.rgb(0, 0, 0), glow = Color.WHITE, pattern = "domain"),
        Skin("blackflash", "Black Flash", Color.rgb(10, 10, 12), icon = Color.rgb(255, 60, 60), glow = Color.rgb(255, 30, 30), pattern = "blackflash"),
        Skin("cursedenergy", "Cursed Energy", Color.rgb(20, 10, 40), icon = Color.rgb(180, 160, 255), glow = Color.rgb(120, 70, 255), pattern = "flames"),
        // Deltarune & Undertale
        Skin("savepoint", "Save Point", Color.rgb(12, 10, 30), glow = Color.rgb(255, 230, 90), pattern = "savepoint"),
        Skin("bigshot", "Big Shot", Color.rgb(255, 90, 180), idle2 = Color.rgb(255, 225, 40), icon = Color.rgb(30, 20, 40), glow = Color.rgb(255, 230, 80), pattern = "bigshot"),
        Skin("cardcastle", "Card Castle", Color.rgb(25, 25, 35), glow = Color.rgb(230, 40, 60), pattern = "suits"),
        Skin("darkfountain", "Dark Fountain", Color.rgb(5, 5, 10), glow = Color.WHITE, pattern = "fountain"),
        Skin("kromer", "Kromer", Color.rgb(255, 205, 60), idle2 = Color.rgb(200, 140, 20), icon = Color.rgb(90, 55, 0), glow = Color.rgb(255, 225, 120), pattern = "coin"),
        Skin("determination", "Determination", Color.rgb(0, 0, 0), glow = Color.rgb(255, 30, 30), pattern = "soul"),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]

    private val pictures = HashMap<String, android.graphics.Bitmap?>()

    /** The built-in skins plus the ones you made. */
    fun all(c: android.content.Context): List<Skin> = all + SkinArt.list(c).mapNotNull { cs ->
        when (cs.kind) {
            "image" -> {
                val bmp = pictures.getOrPut(cs.id) { android.graphics.BitmapFactory.decodeFile(SkinArt.imageFile(c, cs.id).path) }
                    ?: return@mapNotNull null
                Skin(cs.id, cs.title, Color.BLACK, pattern = "image", image = bmp, showMic = false, custom = true)
            }
            "ai" -> {
                val spec = cs.spec ?: return@mapNotNull null
                val bg = spec.optJSONArray("background")
                val c1 = SkinArt.color(bg?.opt(0) ?: spec.opt("background"), Color.rgb(58, 91, 217))
                val c2 = bg?.opt(1)?.let { SkinArt.color(it, c1) }
                val mic = spec.opt("mic")
                Skin(cs.id, cs.title, c1, idle2 = c2, icon = SkinArt.color(mic, Color.WHITE),
                    glow = spec.opt("glow")?.let { if (it is String) SkinArt.color(it, Color.WHITE) else null },
                    pattern = "art", art = spec, showMic = mic is String, custom = true)
            }
            else -> null
        }
    }

    fun byId(c: android.content.Context, id: String?) = all(c).firstOrNull { it.id == id } ?: all[0]
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
    /** "Talk like" mode on: a small purple badge, so you know everything gets rewritten. */
    var persona = false
        set(v) { field = v; invalidate() }
    var skin: Skins.Skin = Skins.all[0]
        set(v) { field = v; invalidate() }
    /** Being moved: a highlighted outline. */
    var moving = false
        set(v) { field = v; invalidate() }
    var seconds = 0
    /** Animated skins move while idle (off = still pictures, a little less battery). */
    var animated = true
        set(v) { field = v; invalidate() }

    /** Seconds, for the animations. */
    private val t get() = (android.os.SystemClock.uptimeMillis() % 3_600_000L) / 1000f
    private val animatedPatterns = setOf("rainbow", "hollowpurple", "infinity", "domain", "blackflash", "flames", "savepoint",
        "soul", "galaxy", "bigshot", "fountain", "eye")
    private fun phase() = if (animated) t else 0f

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
            "diamond" -> {
                val w = r * 1.1f
                path.moveTo(cx, cy - w); path.lineTo(cx + w * 0.85f, cy); path.lineTo(cx, cy + w); path.lineTo(cx - w * 0.85f, cy)
                path.close()
            }
            "hexagon" -> {
                for (i in 0 until 6) {
                    val a = Math.toRadians((-90 + i * 60).toDouble())
                    val x = cx + (r * 1.05f * cos(a)).toFloat()
                    val y = cy + (r * 1.05f * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            else -> path.addCircle(cx, cy, r, Path.Direction.CW)
        }
        return path
    }

    private val pat = Paint(Paint.ANTI_ALIAS_FLAG)
    private val patStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val patPath = Path()

    /** Four-pointed twinkle star. */
    private fun drawSparkle(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        patPath.reset()
        patPath.moveTo(cx, cy - s)
        patPath.quadTo(cx + s * 0.12f, cy - s * 0.12f, cx + s, cy)
        patPath.quadTo(cx + s * 0.12f, cy + s * 0.12f, cx, cy + s)
        patPath.quadTo(cx - s * 0.12f, cy + s * 0.12f, cx - s, cy)
        patPath.quadTo(cx - s * 0.12f, cy - s * 0.12f, cx, cy - s)
        patPath.close()
        pat.shader = null
        pat.color = color
        pat.maskFilter = BlurMaskFilter(s * 0.25f, BlurMaskFilter.Blur.SOLID)
        c.drawPath(patPath, pat)
        pat.maskFilter = null
    }

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        patPath.reset()
        patPath.moveTo(cx, cy + w * 0.85f)
        patPath.cubicTo(cx - w * 1.4f, cy - w * 0.05f, cx - w * 0.6f, cy - w * 1.1f, cx, cy - w * 0.4f)
        patPath.cubicTo(cx + w * 0.6f, cy - w * 1.1f, cx + w * 1.4f, cy - w * 0.05f, cx, cy + w * 0.85f)
        patPath.close()
        pat.shader = null
        pat.color = color
        pat.maskFilter = BlurMaskFilter(w * 0.18f, BlurMaskFilter.Blur.SOLID)
        c.drawPath(patPath, pat)
        pat.maskFilter = null
    }

    /** The themed skins' artwork, drawn over the bubble (behind the microphone). */
    private fun drawPattern(c: Canvas, cx: Float, cy: Float, r: Float) {
        pat.shader = null; pat.maskFilter = null
        when (skin.pattern) {
            "image" -> skin.image?.let { bmp ->
                c.save(); c.clipPath(shapePath(cx, cy, r))
                c.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                c.restore()
            }
            "art" -> skin.art?.let { spec ->
                c.save(); c.clipPath(shapePath(cx, cy, r))
                SkinArt.drawShapes(c, spec, cx, cy, r)
                c.restore()
            }
            "hollowpurple" -> {
                // A red and a blue orb colliding into purple.
                pat.maskFilter = BlurMaskFilter(r * 0.12f, BlurMaskFilter.Blur.NORMAL)
                val oa = -0.66 + phase() * 1.4
                val orb = r * 0.57f
                pat.color = Color.argb(200, 255, 40, 60); c.drawCircle(cx - (orb * cos(oa)).toFloat(), cy - (orb * sin(oa)).toFloat(), r * 0.28f, pat)
                pat.color = Color.argb(200, 40, 140, 255); c.drawCircle(cx + (orb * cos(oa)).toFloat(), cy + (orb * sin(oa)).toFloat(), r * 0.28f, pat)
                pat.color = Color.argb(140, 230, 190, 255); c.drawCircle(cx, cy, r * 0.42f, pat)
                pat.maskFilter = null
            }
            "infinity" -> {
                // Rings that get closer and closer together (never quite reaching the middle).
                patStroke.color = Color.argb(150, 255, 255, 255)
                val f = (phase() * 0.6f) % 1f
                var rr = r * 0.95f * Math.pow(0.78, f.toDouble()).toFloat()
                var w = 1.6f * d
                while (rr > r * 0.2f) { patStroke.strokeWidth = w; c.drawCircle(cx, cy, rr, patStroke); rr *= 0.78f; w *= 0.85f }
            }
            "domain" -> {
                // A black void with a ring of light and specks of stars.
                pat.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(0, 0, 0), Color.rgb(20, 10, 50), Color.rgb(110, 60, 200)),
                    floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, pat); pat.shader = null
                patStroke.color = Color.WHITE; patStroke.strokeWidth = 1.4f * d
                patStroke.maskFilter = BlurMaskFilter(2 * d, BlurMaskFilter.Blur.SOLID)
                c.drawCircle(cx, cy, r * 0.82f, patStroke); patStroke.maskFilter = null
                pat.color = Color.argb(230, 255, 255, 255)
                for (i in 0 until 14) {
                    val a = i * 2.39996
                    val rr = r * (0.2f + (i % 5) * 0.12f)
                    val tw = if (animated) 0.35f + 0.65f * kotlin.math.abs(sin(phase() * 1.7 + i * 1.3)).toFloat() else 0.9f
                    pat.color = Color.argb((255 * tw).toInt(), 255, 255, 255)
                    c.drawCircle(cx + (rr * cos(a + phase() * 0.15)).toFloat(), cy + (rr * sin(a + phase() * 0.15)).toFloat(), (0.6f + (i % 3) * 0.4f) * d, pat)
                }
            }
            "blackflash" -> {
                // Jagged red-black sparks.
                patStroke.strokeWidth = 2.2f * d
                for ((k, a0) in listOf(0.3, 2.4, 4.3).withIndex()) {
                    patPath.reset()
                    var x = cx + (r * 0.95f * cos(a0)).toFloat(); var y = cy + (r * 0.95f * sin(a0)).toFloat()
                    patPath.moveTo(x, y)
                    for (j in 1..4) {
                        val t = 0.95f - j * 0.17f
                        val a = a0 + (if (j % 2 == 0) 0.35 else -0.35)
                        x = cx + (r * t * cos(a)).toFloat(); y = cy + (r * t * sin(a)).toFloat()
                        patPath.lineTo(x, y)
                    }
                    val on = !animated || sin(phase() * 11.0 + k * 2.1) > -0.4
                    if (!on) continue
                    patStroke.color = if (k == 1) Color.rgb(20, 0, 0) else Color.rgb(255, 40, 40)
                    patStroke.maskFilter = BlurMaskFilter(2.5f * d, BlurMaskFilter.Blur.SOLID)
                    c.drawPath(patPath, patStroke)
                }
                patStroke.maskFilter = null
            }
            "flames" -> {
                // Wisps rising around the edge.
                pat.maskFilter = BlurMaskFilter(r * 0.1f, BlurMaskFilter.Blur.NORMAL)
                for (i in 0 until 7) {
                    val a = Math.PI * (0.15 + i * 0.1)
                    val x = cx + (r * 0.75f * cos(a)).toFloat()
                    val y = cy + (r * 0.65f * sin(a)).toFloat() - (if (animated) (r * 0.06f * sin(phase() * 4.0 + i)).toFloat() else 0f)
                    pat.color = if (i % 2 == 0) Color.argb(170, 120, 80, 255) else Color.argb(150, 60, 140, 255)
                    c.drawOval(RectF(x - r * 0.12f, y - r * 0.35f, x + r * 0.12f, y + r * 0.05f), pat)
                }
                pat.maskFilter = null
            }
            "bigshot" -> {
                // Diagonal sale stripes and a sparkle.
                c.save(); c.clipPath(shapePath(cx, cy, r))
                patStroke.color = Color.argb(70, 255, 255, 255); patStroke.strokeWidth = r * 0.12f
                var o = -r * 2 + (phase() * r * 0.3f) % (r * 0.38f)
                while (o < r * 2) { c.drawLine(cx + o - r, cy + r, cx + o + r, cy - r, patStroke); o += r * 0.38f }
                c.restore()
                drawSparkle(c, cx + r * 0.55f, cy - r * 0.5f, r * 0.22f, Color.WHITE)
            }
            "suits" -> {
                // Card suits around the edge.
                text.textSize = r * 0.34f
                val suits = listOf("♠" to Color.WHITE, "♥" to Color.rgb(240, 50, 70), "♣" to Color.WHITE, "♦" to Color.rgb(240, 50, 70))
                for ((i, sv) in suits.withIndex()) {
                    val a = Math.toRadians(-90.0 + i * 90)
                    text.color = sv.second
                    c.drawText(sv.first, cx + (r * 0.68f * cos(a)).toFloat(), cy + (r * 0.68f * sin(a)).toFloat() - (text.ascent() + text.descent()) / 2, text)
                }
            }
            "fountain" -> {
                // A white beam of light straight up, with specks around it.
                pat.shader = LinearGradient(cx - r * 0.18f, 0f, cx + r * 0.18f, 0f,
                    intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(230, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                    null, Shader.TileMode.CLAMP)
                c.save(); c.clipPath(shapePath(cx, cy, r))
                c.drawRect(cx - r * 0.18f, cy - r, cx + r * 0.18f, cy + r, pat)
                c.restore(); pat.shader = null
                pat.color = Color.argb(200, 255, 255, 255)
                for ((fx, fy) in listOf(-0.5f to -0.3f, 0.45f to 0.2f, -0.35f to 0.5f, 0.55f to -0.45f, 0.3f to 0.65f)) {
                    // Drift upwards and wrap around.
                    val y = ((fy + 1f - (phase() * 0.25f + fx) % 2f + 2f) % 2f) - 1f
                    c.drawCircle(cx + fx * r, cy + y * r * 0.9f, 1.1f * d, pat)
                }
            }
            "coin" -> {
                // An embossed rim.
                patStroke.color = Color.argb(150, 255, 245, 200); patStroke.strokeWidth = 2 * d
                c.drawCircle(cx, cy, r * 0.82f, patStroke)
                patStroke.color = Color.argb(90, 120, 70, 0); patStroke.strokeWidth = 1.2f * d
                c.drawCircle(cx, cy, r * 0.74f, patStroke)
            }
        }
        pat.shader = null; pat.maskFilter = null
    }

    private val eyePath = Path()
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    /** Six Eyes: a glowing blue eye with white lashes. */
    private fun drawEye(c: Canvas, cx: Float, cy: Float, r: Float) {
        val w = r * 0.8f
        val lid = r * 0.78f
        eyePath.reset()
        eyePath.moveTo(cx - w, cy)
        eyePath.quadTo(cx, cy - lid, cx + w, cy)
        eyePath.quadTo(cx, cy + lid, cx - w, cy)
        eyePath.close()
        // White of the eye.
        eyePaint.shader = null
        eyePaint.color = Color.rgb(236, 246, 255)
        c.drawPath(eyePath, eyePaint)
        c.save()
        c.clipPath(eyePath)
        // Bright blue iris, lighter in the middle.
        val ir = r * 0.43f
        eyePaint.shader = RadialGradient(cx, cy, ir,
            intArrayOf(Color.rgb(235, 255, 255), Color.rgb(110, 225, 255), Color.rgb(20, 140, 240), Color.rgb(5, 60, 170)),
            floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, ir, eyePaint)
        eyePaint.shader = null
        // Fine streaks in the iris.
        lash.color = Color.argb(150, 210, 250, 255)
        lash.strokeWidth = 0.8f * d
        for (i in 0 until 16) {
            val a = Math.toRadians(i * 22.5 + phase() * 25)   // the iris slowly turns
            c.drawLine(cx + (ir * 0.32f * cos(a)).toFloat(), cy + (ir * 0.32f * sin(a)).toFloat(),
                cx + (ir * 0.9f * cos(a)).toFloat(), cy + (ir * 0.9f * sin(a)).toFloat(), lash)
        }
        eyePaint.color = Color.rgb(8, 30, 80)
        c.drawCircle(cx, cy, r * 0.12f, eyePaint)
        eyePaint.color = Color.WHITE
        c.drawCircle(cx - r * 0.14f, cy - r * 0.14f, r * 0.075f, eyePaint)
        c.restore()
        // White lid line and lashes.
        lash.color = Color.rgb(245, 250, 255)
        lash.strokeWidth = 2.2f * d
        eyePath.reset()
        eyePath.moveTo(cx - w, cy)
        eyePath.quadTo(cx, cy - lid, cx + w, cy)
        lash.style = Paint.Style.STROKE
        c.drawPath(eyePath, lash)
        lash.strokeWidth = 1.8f * d
        for (t in floatArrayOf(0.12f, 0.28f, 0.5f, 0.72f, 0.88f)) {
            val u = 1 - t
            val x = u * u * (cx - w) + 2 * u * t * cx + t * t * (cx + w)
            val y = u * u * cy + 2 * u * t * (cy - lid) + t * t * cy
            val dx = x - cx
            val dy = y - (cy + r * 0.3f)
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            c.drawLine(x, y, x + dx / len * r * 0.2f, y + dy / len * r * 0.2f, lash)
        }
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
                if (skin.pattern == "rainbow") fill.shader = SweepGradient(cx, cy, intArrayOf(Color.rgb(255, 70, 70),
                    Color.rgb(255, 190, 40), Color.rgb(80, 220, 90), Color.rgb(40, 170, 255), Color.rgb(170, 90, 255), Color.rgb(255, 70, 70)), null)
                    .apply { setLocalMatrix(android.graphics.Matrix().apply { setRotate(phase() * 60f, cx, cy) }) }
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
        if (state == State.IDLE && skin.pattern == "galaxy") {
            // A few stars.
            for ((fx, fy, fs) in listOf(Triple(-0.5f, -0.35f, 1.4f), Triple(0.45f, -0.5f, 1f), Triple(0.55f, 0.3f, 1.6f),
                    Triple(-0.3f, 0.55f, 1f), Triple(0.1f, -0.7f, 0.8f), Triple(-0.65f, 0.15f, 0.9f), Triple(0.25f, 0.62f, 0.8f))) {
                val tw = if (animated) (0.45f + 0.55f * kotlin.math.abs(sin(phase() * 2.2 + fx * 7 + fy * 5)).toFloat()) else 0.85f
                iconFill.color = Color.argb((255 * tw).toInt(), 255, 255, 255)
                c.drawCircle(cx + fx * r, cy + fy * r, fs * d * (0.7f + 0.5f * tw), iconFill)
            }
        }
        if (state == State.IDLE) drawPattern(c, cx, cy, r)
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

        if (persona) {
            val br = r * 0.28f
            val bx = cx - r * 0.72f
            val by = cy + r * 0.72f
            iconFill.color = Color.rgb(156, 92, 255)
            c.drawCircle(bx, by, br, iconFill)
            ring.color = Color.WHITE
            ring.strokeWidth = 1.5f * d
            c.drawCircle(bx, by, br, ring)
            iconFill.color = ink
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

        if (skin.pattern == "eye") { drawEye(c, cx, cy, r); keepMoving(); return }
        if (!skin.showMic) return
        val pulse = if (animated) 1f + 0.08f * sin(phase() * 4.0).toFloat() else 1f
        if (skin.pattern == "savepoint") { drawSparkle(c, cx, cy, r * 0.62f * pulse, Color.rgb(255, 225, 70)); keepMoving(); return }
        if (skin.pattern == "soul") { drawHeart(c, cx, cy + r * 0.05f, r * 0.5f * pulse, Color.rgb(255, 20, 30)); keepMoving(); return }

        // Microphone icon.
        icon.strokeWidth = 2.4f * s
        val y = cy + oy
        c.drawRoundRect(RectF(cx - 5 * s, y - 11 * s, cx + 5 * s, y + 3 * s), 5 * s, 5 * s, iconFill)
        c.drawArc(RectF(cx - 9 * s, y - 7 * s, cx + 9 * s, y + 7.5f * s), 0f, 180f, false, icon)
        c.drawLine(cx, y + 7.5f * s, cx, y + 11.5f * s, icon)
        keepMoving()
    }

    /** Animated skin, idle and on screen: draw the next frame (about 30 a second). */
    private fun keepMoving() {
        if (animated && state == State.IDLE && skin.pattern in animatedPatterns && isShown) postInvalidateDelayed(33)
    }
}
