package com.voicebubble

import kotlin.math.sqrt

/**
 * Finding the quiet parts of a recording (100 ms frames at 16 kHz):
 * - [findCut]: a pause where the part said so far can already be turned into text
 * - [trimSilence]: drops silence at the start/end and shortens long pauses before Whisper reads it
 */
object Audio {
    const val FRAME = 1600

    fun frameRms(a: FloatArray): FloatArray {
        val n = a.size / FRAME
        return FloatArray(n) { f ->
            var s = 0.0
            val base = f * FRAME
            for (i in base until base + FRAME) s += a[i] * a[i]
            sqrt(s / FRAME).toFloat()
        }
    }

    /** Loudness below which a frame counts as silence: a bit above the room's background noise. */
    fun threshold(rms: FloatArray): Float {
        if (rms.isEmpty()) return 0.01f
        val sorted = rms.sortedArray()
        val floor = sorted[(sorted.size * 0.15).toInt().coerceAtMost(sorted.size - 1)]
        val peak = sorted.last()
        return maxOf(0.006f, minOf(floor * 3f, peak * 0.4f))
    }

    /**
     * Where the part said so far can be cut off and turned into text: the middle of the first pause
     * of at least [minPauseSec] after at least [minSpeechSec] of audio with some speech in it.
     * -1 when there's no good place yet.
     */
    fun findCut(a: FloatArray, minSpeechSec: Double = 3.0, minPauseSec: Double = 0.5): Int {
        val rms = frameRms(a)
        if (rms.size < 4) return -1
        val thr = threshold(rms)
        val minSpeech = (minSpeechSec * 10).toInt()
        val minPause = (minPauseSec * 10).toInt()
        var spoke = 0
        var runStart = -1
        for (f in rms.indices) {
            if (rms[f] >= thr) {
                spoke++
                runStart = -1
            } else {
                if (runStart < 0) runStart = f
                if (f - runStart + 1 >= minPause && runStart >= minSpeech && spoke >= 5) {
                    return ((runStart + f + 1) / 2) * FRAME
                }
            }
        }
        return -1
    }

    /**
     * Removes the silence before you start and after you stop (keeps 0.25 s), and shortens pauses
     * longer than 0.8 s to 0.4 s. Less audio = faster, and Whisper invents fewer words in silence.
     */
    fun trimSilence(a: FloatArray): FloatArray {
        val rms = frameRms(a)
        if (rms.isEmpty()) return a
        val thr = threshold(rms)
        val loud = rms.indices.filter { rms[it] >= thr }
        if (loud.isEmpty()) return a
        val first = maxOf(0, loud.first() - 2)
        val last = minOf(rms.size - 1, loud.last() + 2)
        val out = FloatArray(a.size)
        var n = 0
        fun copyFrame(k: Int) { System.arraycopy(a, k * FRAME, out, n, FRAME); n += FRAME }
        var f = first
        while (f <= last) {
            if (rms[f] < thr) {
                var e = f
                while (e + 1 <= last && rms[e + 1] < thr) e++
                val len = e - f + 1
                val keep = if (len > 8) listOf(f, f + 1, e - 1, e) else (f..e).toList()
                for (k in keep) copyFrame(k)
                f = e + 1
            } else {
                copyFrame(f)
                f++
            }
        }
        // Whatever is left after the last whole frame belongs to the end; it's padding anyway.
        return out.copyOf(n)
    }
}
