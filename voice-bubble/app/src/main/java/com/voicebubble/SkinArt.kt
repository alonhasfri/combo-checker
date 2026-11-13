package com.voicebubble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Your own skins: a picture you upload, or a little drawing the AI designs from a description.
 * Saved on the phone; they show up in Settings → Bubble skin next to the built-in ones.
 */
object SkinArt {
    /** What the AI is told: draw on a 100×100 circle with a few simple shapes. */
    const val AI_FORMAT = """You design the look of a small round floating button (a "bubble", about 1.5 cm on a phone screen).
Draw it with simple vector shapes on a 100×100 canvas; the bubble is the circle centred at (50,50) with radius 50, and
anything outside it is cut off. Make it bold, recognisable and colourful at that small size: big simple shapes,
strong outlines, few details. Draw it as your own original take on the idea.
Reply with ONLY a JSON object, no other text:
{
  "name": "short name, 1-3 words",
  "background": ["#RRGGBB", "#RRGGBB"],   // one colour, or two for a diagonal gradient
  "glow": "#RRGGBB" or null,              // soft glow around the bubble
  "mic": "#RRGGBB" or null,               // colour of the microphone drawn in the middle, or null to draw no microphone
  "shapes": [ ... drawn in order, up to 40 ... ]
}
Shapes (colours are "#RRGGBB" or "#AARRGGBB"; add "stroke": width to draw only the outline):
{"type":"circle","x":50,"y":50,"r":20,"color":"#FFFFFF"}
{"type":"ellipse","x":50,"y":50,"rx":30,"ry":15,"color":"#FFFFFF"}
{"type":"rect","x":20,"y":30,"w":60,"h":20,"radius":5,"color":"#FFFFFF"}
{"type":"line","x1":10,"y1":10,"x2":90,"y2":90,"width":4,"color":"#000000"}
{"type":"polygon","points":[[50,10],[90,90],[10,90]],"color":"#FFCC00"}
{"type":"path","d":"M 20 50 Q 50 20 80 50","color":"#000000","stroke":3}   // SVG path: M L H V Q C Z (also lowercase)
{"type":"text","x":50,"y":60,"size":30,"text":"★","color":"#FFFFFF"}     // x,y = centre of the text"""

    class Custom(val id: String, val title: String, val kind: String, val spec: JSONObject?)

    private fun sp(c: Context) = c.getSharedPreferences("skins", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "skins").apply { mkdirs() }
    fun imageFile(c: Context, id: String) = File(dir(c), "$id.png")

    fun list(c: Context): List<Custom> {
        val arr = try { JSONArray(sp(c).getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Custom(o.optString("id"), o.optString("title", "My skin"), o.optString("kind"), o.optJSONObject("spec"))
        }
    }

    private fun save(c: Context, items: List<Custom>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("kind", it.kind).apply { it.spec?.let { s -> put("spec", s) } }) }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun delete(c: Context, id: String) {
        save(c, list(c).filter { it.id != id })
        imageFile(c, id).delete()
    }

    /** Saves a picture as a skin: cut to a square from the middle, 320 px. Returns the new skin's id. */
    fun addPicture(c: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 320) sample *= 2
        val src = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, 320, 320, true)
        val id = "pic_" + System.currentTimeMillis()
        imageFile(c, id).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        save(c, list(c) + Custom(id, "My picture", "image", null))
        return id
    }

    /** Reads the AI's answer; null when it isn't a usable drawing. */
    fun parseSpec(answer: String): JSONObject? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        // Some AIs leave // comments in; take them out.
        val json = answer.substring(start, end + 1).lines().joinToString("\n") { line ->
            val i = line.indexOf("//")
            if (i >= 0 && line.substring(0, i).count { it == '"' } % 2 == 0) line.substring(0, i) else line
        }
        return try {
            JSONObject(json).takeIf { it.has("background") || it.has("shapes") }
        } catch (e: Exception) { null }
    }

    fun addDesign(c: Context, spec: JSONObject): String {
        val id = "ai_" + System.currentTimeMillis()
        val name = spec.optString("name").trim().take(24).ifEmpty { "AI skin" }
        save(c, list(c) + Custom(id, name, "ai", spec))
        return id
    }

    // ---------------- drawing ----------------

    fun color(v: Any?, fallback: Int): Int = try {
        if (v is String && v.startsWith("#")) Color.parseColor(v) else fallback
    } catch (e: Exception) { fallback }

    // Made on first use (not when this file loads), so the parts that only read text work anywhere.
    private val fill by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val textPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    /** Draws the AI's shapes inside a circle at (cx, cy) with radius r. */
    fun drawShapes(cv: Canvas, spec: JSONObject, cx: Float, cy: Float, r: Float) {
        val shapes = spec.optJSONArray("shapes") ?: return
        val k = r / 50f
        fun X(v: Double) = cx + ((v - 50) * k).toFloat()
        fun Y(v: Double) = cy + ((v - 50) * k).toFloat()
        for (i in 0 until minOf(shapes.length(), 60)) {
            val s = shapes.optJSONObject(i) ?: continue
            fill.color = color(s.opt("color"), Color.WHITE)
            val stroke = s.optDouble("stroke", 0.0)
            fill.style = if (stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
            fill.strokeWidth = (stroke * k).toFloat()
            fill.strokeCap = Paint.Cap.ROUND; fill.strokeJoin = Paint.Join.ROUND
            runCatching {
                when (s.optString("type")) {
                    "circle" -> cv.drawCircle(X(s.optDouble("x", 50.0)), Y(s.optDouble("y", 50.0)), (s.optDouble("r", 10.0) * k).toFloat(), fill)
                    "ellipse" -> {
                        val x = s.optDouble("x", 50.0); val y = s.optDouble("y", 50.0)
                        val rx = s.optDouble("rx", 10.0); val ry = s.optDouble("ry", 10.0)
                        cv.drawOval(RectF(X(x - rx), Y(y - ry), X(x + rx), Y(y + ry)), fill)
                    }
                    "rect" -> {
                        val x = s.optDouble("x", 0.0); val y = s.optDouble("y", 0.0)
                        val rad = (s.optDouble("radius", 0.0) * k).toFloat()
                        cv.drawRoundRect(RectF(X(x), Y(y), X(x + s.optDouble("w", 10.0)), Y(y + s.optDouble("h", 10.0))), rad, rad, fill)
                    }
                    "line" -> {
                        fill.style = Paint.Style.STROKE
                        fill.strokeWidth = (s.optDouble("width", if (stroke > 0) stroke else 3.0) * k).toFloat()
                        cv.drawLine(X(s.optDouble("x1")), Y(s.optDouble("y1")), X(s.optDouble("x2")), Y(s.optDouble("y2")), fill)
                    }
                    "polygon" -> {
                        val pts = s.optJSONArray("points") ?: return@runCatching
                        val p = Path()
                        for (j in 0 until pts.length()) {
                            val pt = pts.optJSONArray(j) ?: continue
                            val x = X(pt.optDouble(0)); val y = Y(pt.optDouble(1))
                            if (j == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        p.close(); cv.drawPath(p, fill)
                    }
                    "path" -> svgPath(s.optString("d"), ::X, ::Y)?.let { cv.drawPath(it, fill) }
                    "text" -> {
                        textPaint.color = fill.color
                        textPaint.textSize = (s.optDouble("size", 20.0) * k).toFloat()
                        val y = Y(s.optDouble("y", 50.0)) - (textPaint.ascent() + textPaint.descent()) / 2
                        cv.drawText(s.optString("text").take(6), X(s.optDouble("x", 50.0)), y, textPaint)
                    }
                }
            }
        }
        fill.style = Paint.Style.FILL
    }

    /** A small SVG path reader: M L H V Q C Z, absolute and relative. */
    fun svgPath(d: String, X: (Double) -> Float, Y: (Double) -> Float): Path? {
        val tokens = Regex("[MLHVQCZmlhvqcz]|-?\\d*\\.?\\d+(?:e-?\\d+)?").findAll(d).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val p = Path()
        var i = 0
        var cmd = 'M'
        var x = 0.0; var y = 0.0; var sx = 0.0; var sy = 0.0
        fun num(): Double = tokens.getOrNull(i++)?.toDoubleOrNull() ?: throw IllegalStateException()
        try {
            while (i < tokens.size) {
                val t = tokens[i]
                if (t[0].isLetter()) { cmd = t[0]; i++; if (cmd == 'Z' || cmd == 'z') { p.close(); x = sx; y = sy; continue } }
                val rel = cmd.isLowerCase()
                when (cmd.uppercaseChar()) {
                    'M' -> { var nx = num(); var ny = num(); if (rel) { nx += x; ny += y }; x = nx; y = ny; sx = x; sy = y
                        p.moveTo(X(x), Y(y)); cmd = if (rel) 'l' else 'L' }
                    'L' -> { var nx = num(); var ny = num(); if (rel) { nx += x; ny += y }; x = nx; y = ny; p.lineTo(X(x), Y(y)) }
                    'H' -> { var nx = num(); if (rel) nx += x; x = nx; p.lineTo(X(x), Y(y)) }
                    'V' -> { var ny = num(); if (rel) ny += y; y = ny; p.lineTo(X(x), Y(y)) }
                    'Q' -> { var x1 = num(); var y1 = num(); var nx = num(); var ny = num()
                        if (rel) { x1 += x; y1 += y; nx += x; ny += y }
                        p.quadTo(X(x1), Y(y1), X(nx), Y(ny)); x = nx; y = ny }
                    'C' -> { var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var nx = num(); var ny = num()
                        if (rel) { x1 += x; y1 += y; x2 += x; y2 += y; nx += x; ny += y }
                        p.cubicTo(X(x1), Y(y1), X(x2), Y(y2), X(nx), Y(ny)); x = nx; y = ny }
                    else -> i++
                }
            }
        } catch (e: IllegalStateException) { /* draw what was read */ }
        return p
    }
}
