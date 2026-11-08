package com.voicebubble

/** Spotting a word you corrected after it was typed, so it can be added to the dictionary. */
object Learn {
    private fun isWordChar(ch: Char) = ch.isLetterOrDigit() || ch == '\'' || ch == '’' || ch == '-'

    /**
     * Compares the text it typed ([before]) with the text now ([after]). If you replaced a word or two
     * inside the dictated part ([from]..[to] in [before]) with a different word or two, returns the new
     * wording (e.g. "Go Joe" → "Gojo" gives "Gojo"). Null for anything else (typing more, deleting…).
     */
    fun correction(before: String, after: String, from: Int, to: Int): String? {
        if (before == after) return null
        var p = 0
        while (p < before.length && p < after.length && before[p] == after[p]) p++
        var s = 0
        while (s < before.length - p && s < after.length - p &&
            before[before.length - 1 - s] == after[after.length - 1 - s]) s++
        // Only text that was replaced counts: just typing more, or just deleting, isn't a correction.
        if (before.length - s - p <= 0 || after.length - s - p <= 0) return null
        // Widen to whole words on both sides.
        while (p > 0 && isWordChar(before[p - 1])) p--
        while (s > 0 && isWordChar(before[before.length - s])) s--
        val oldSeg = before.substring(p, before.length - s).trim()
        val newSeg = after.substring(p, after.length - s).trim()
        if (oldSeg.isEmpty() || newSeg.isEmpty()) return null
        if (p < from || before.length - s > to) return null                 // outside what it typed
        val oldWords = oldSeg.split(Regex("\\s+"))
        val newWords = newSeg.split(Regex("\\s+"))
        if (oldWords.size > 3 || newWords.size > 3) return null              // a rewrite, not a fix
        if (!newWords.all { w -> w.any { it.isLetter() } && w.all { isWordChar(it) } }) return null
        if (newSeg.equals(oldSeg, ignoreCase = false)) return null
        if (newSeg.length < 2) return null
        return newSeg
    }
}
