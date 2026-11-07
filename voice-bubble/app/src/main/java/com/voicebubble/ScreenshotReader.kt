package com.voicebubble

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

/**
 * Reads the words off a screenshot of another app's dictionary or shortcut list, on the phone.
 * Lines that sit side by side (e.g. "btw    by the way") become "btw => by the way".
 */
object ScreenshotReader {
    fun read(c: Context, uri: Uri, done: (String?) -> Unit) {
        val image = try { InputImage.fromFilePath(c, uri) } catch (e: Exception) { done(null); return }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { result ->
                class L(val text: String, val left: Int, val cy: Int, val h: Int)
                val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                    val b = l.boundingBox ?: return@mapNotNull null
                    L(l.text.trim(), b.left, b.centerY(), b.height())
                }.filter { it.text.isNotEmpty() }.sortedBy { it.cy }

                // Group lines into rows by height on screen.
                val rows = mutableListOf<MutableList<L>>()
                for (l in lines) {
                    val row = rows.lastOrNull()
                    if (row != null && abs(row[0].cy - l.cy) < maxOf(row[0].h, l.h) * 0.6) row += l else rows += mutableListOf(l)
                }
                val out = rows.joinToString("\n") { r ->
                    val s = r.sortedBy { it.left }
                    if (s.size >= 2) s[0].text + " => " + s.drop(1).joinToString(" ") { it.text } else s[0].text
                }
                done(out)
            }
            .addOnFailureListener { done(null) }
    }
}
