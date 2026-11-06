package com.voicebubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs

data class Shortcut(val say: String, val write: String)

/**
 * Your dictionary (words Whisper should spell your way) and shortcuts (say "btw", get "by the way").
 * Stored on the phone. Applied to every transcription.
 */
object Words {
    private fun sp(c: Context) = c.getSharedPreferences("words", Context.MODE_PRIVATE)

    fun dictionary(c: Context): List<String> {
        val a = JSONArray(sp(c).getString("dict", "[]"))
        return List(a.length()) { a.getString(it) }
    }

    fun saveDictionary(c: Context, list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        sp(c).edit().putString("dict", JSONArray(clean).toString()).apply()
    }

    fun shortcuts(c: Context): List<Shortcut> {
        val a = JSONArray(sp(c).getString("short", "[]"))
        return List(a.length()) { a.getJSONObject(it).let { o -> Shortcut(o.getString("say"), o.getString("write")) } }
    }

    fun saveShortcuts(c: Context, list: List<Shortcut>) {
        val clean = list.map { Shortcut(it.say.trim(), it.write) }
            .filter { it.say.isNotEmpty() && it.write.isNotEmpty() }
            .distinctBy { norm(it.say) }
        val a = JSONArray()
        clean.forEach { a.put(JSONObject().put("say", it.say).put("write", it.write)) }
        sp(c).edit().putString("short", a.toString()).apply()
    }

    // ---------------- applying them to a transcription ----------------

    /** Dictionary words given to Whisper up front, so it already leans towards your spellings. */
    fun prompt(c: Context): String {
        val words = dictionary(c)
        if (words.isEmpty()) return ""
        val sb = StringBuilder()
        for (w in words) {
            if (sb.length + w.length > 600) break
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(w)
        }
        return sb.append('.').toString()
    }

    fun apply(c: Context, text: String): String =
        applyShortcuts(applyDictionary(text, dictionary(c), shortcuts(c)), shortcuts(c))

    private class Tok(val start: Int, val end: Int, val text: String) { val n = norm(text) }

    private val wordRe = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")
    private fun tokens(s: String) = wordRe.findAll(s).map { Tok(it.range.first, it.range.last + 1, it.value) }.toList()

    /** Lowercase, no accents, letters and digits only. */
    fun norm(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return d.filter { it.isLetterOrDigit() }
    }

    /** Rough "sounds the same" form for comparing spellings. */
    private fun sound(s: String): String {
        var t = norm(s).replace("ph", "f").replace("ck", "k").replace("kh", "h")
            .replace("ch", "\u0001")                 // keep "ch" as its own sound
            .replace(Regex("c(?=[eiy])"), "s")       // "ce", "ci" sound like s
            .replace('c', 'k').replace("\u0001", "ch") // other c sounds like k
            .replace('z', 's')
        val sb = StringBuilder()
        for (ch in t) if (sb.isEmpty() || sb.last() != ch) sb.append(ch)
        t = sb.toString()
        return t
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** How many letters may differ: short words must match exactly, long ones may be off by 1-2. */
    private fun allowed(len: Int) = when {
        len <= 4 -> 0
        len <= 8 -> 1
        else -> 2
    }

    private class Rep(val start: Int, val end: Int, val with: String)

    private fun replaceAll(text: String, reps: List<Rep>): String {
        val sb = StringBuilder(text)
        for (r in reps.sortedByDescending { it.start }) sb.replace(r.start, r.end, r.with)
        return sb.toString()
    }

    /** Words that sound like a dictionary entry are written the dictionary's way. */
    fun applyDictionary(text: String, dict: List<String>, shorts: List<Shortcut> = emptyList()): String {
        if (dict.isEmpty()) return text
        val toks = tokens(text)
        if (toks.isEmpty()) return text
        val used = BooleanArray(toks.size)
        // Don't "correct" a word that is a shortcut you meant to say.
        val shortKeys = shorts.map { norm(it.say) }.toSet()
        toks.forEachIndexed { i, t -> if (t.n in shortKeys) used[i] = true }
        val reps = mutableListOf<Rep>()
        val entries = dict.map { it to tokens(it).size }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenByDescending { it.first.length })

        for ((entry, size) in entries) {
            val key = sound(entry)
            if (key.length < 2) continue
            // Same number of words (may be slightly misspelled), or split/joined differently (must match exactly).
            val sizes = linkedSetOf(size, size + 1, size + 2, size - 1).filter { it >= 1 }
            for (k in sizes) {
                var i = 0
                while (i + k <= toks.size) {
                    if ((i until i + k).any { used[it] }) { i++; continue }
                    val window = toks.subList(i, i + k)
                    val cand = sound(window.joinToString("") { it.text })
                    val ok = if (k == size) {
                        val a = allowed(maxOf(cand.length, key.length))
                        cand.length >= 3 && abs(cand.length - key.length) <= a && (cand == key || (cand[0] == key[0] && lev(cand, key) <= a))
                    } else cand == key
                    if (ok) {
                        val s = window.first().start
                        val e = window.last().end
                        if (text.substring(s, e) != entry) reps += Rep(s, e, entry)
                        for (j in i until i + k) used[j] = true
                        i += k
                    } else i++
                }
            }
        }
        return replaceAll(text, reps)
    }

    /** "btw" becomes "by the way". Also catches "B.T.W." and "b t w". */
    fun applyShortcuts(text: String, shorts: List<Shortcut>): String {
        if (shorts.isEmpty()) return text
        val toks = tokens(text)
        if (toks.isEmpty()) return text
        val used = BooleanArray(toks.size)
        val reps = mutableListOf<Rep>()
        val sorted = shorts.map { it to tokens(it.say).map { t -> t.n } }.filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }

        for ((sc, say) in sorted) {
            val spelled = if (say.size == 1 && say[0].length in 2..8 && say[0].all { it.isLetter() }) say[0] else null
            var i = 0
            while (i < toks.size) {
                var k = 0
                if (i + say.size <= toks.size && (i until i + say.size).none { used[it] } &&
                    (0 until say.size).all { toks[i + it].n == say[it] }) k = say.size
                else if (spelled != null && i + spelled.length <= toks.size &&
                    (i until i + spelled.length).none { used[it] } &&
                    (0 until spelled.length).all { toks[i + it].n.length == 1 && toks[i + it].n[0] == spelled[it] }) k = spelled.length
                if (k == 0) { i++; continue }

                val s = toks[i].start
                var e = toks[i + k - 1].end
                // "B.T.W." – take the last dot too, unless it ends the sentence.
                if (k > 1 && e < text.length && text[e] == '.' && e + 1 < text.length) e++
                var w = sc.write
                val before = text.substring(0, s).trimEnd()
                val sentenceStart = before.isEmpty() || before.last() in ".!?\n"
                if (sentenceStart && w.isNotEmpty() && w[0].isLowerCase()) w = w[0].uppercase() + w.substring(1)
                reps += Rep(s, e, w)
                for (j in i until i + k) used[j] = true
                i += k
            }
        }
        return replaceAll(text, reps)
    }

    // ---------------- import / export ----------------

    /** The text format for moving words between apps and phones (also what Claude is asked to write). */
    fun export(c: Context): String {
        val sb = StringBuilder("# Voice Bubble dictionary and shortcuts\n")
        dictionary(c).forEach { sb.append("word: ").append(it).append('\n') }
        shortcuts(c).forEach { sb.append("short: ").append(it.say).append(" => ").append(it.write.replace("\n", "\\n")).append('\n') }
        return sb.toString()
    }

    class Parsed(val words: List<String>, val shorts: List<Shortcut>)

    private val arrow = Regex("\\s*(=>|->|→|⇒|➔|➜|=)\\s*")

    /** Reads the export format; also plain lists ("word" per line, "btw -> by the way"). */
    fun parse(input: String): Parsed {
        val lines = input.lines().map { it.trim().trim('`').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("```") }
        val tagged = lines.any { it.startsWith("word:", true) || it.startsWith("short:", true) }
        val words = mutableListOf<String>()
        val shorts = mutableListOf<Shortcut>()
        for (raw in lines) {
            var l = raw.removePrefix("- ").removePrefix("• ").removePrefix("* ").trim()
            when {
                l.startsWith("word:", true) -> words += l.substring(5).trim()
                l.startsWith("short:", true) -> splitShort(l.substring(6))?.let { shorts += it }
                tagged -> {} // ignore chatter around a tagged list
                arrow.containsMatchIn(l) -> splitShort(l)?.let { shorts += it }
                else -> words += l
            }
        }
        return Parsed(words.filter { it.isNotBlank() && it.length <= 60 }, shorts)
    }

    private fun splitShort(s: String): Shortcut? {
        val m = arrow.find(s) ?: return null
        val say = s.substring(0, m.range.first).trim().trim('"', '“', '”')
        val write = s.substring(m.range.last + 1).trim().trim('"', '“', '”').replace("\\n", "\n")
        return if (say.isEmpty() || write.isEmpty()) null else Shortcut(say, write)
    }

    /** Adds what isn't there yet. Returns (new words, new shortcuts). */
    fun merge(c: Context, p: Parsed): Pair<Int, Int> {
        val dict = dictionary(c)
        val newWords = p.words.filter { w -> dict.none { it.equals(w, true) } }.distinctBy { it.lowercase() }
        saveDictionary(c, dict + newWords)
        val sc = shortcuts(c)
        val newShorts = p.shorts.filter { s -> sc.none { norm(it.say) == norm(s.say) } }.distinctBy { norm(it.say) }
        saveShortcuts(c, sc + newShorts)
        return newWords.size to newShorts.size
    }

    const val CLAUDE_PROMPT = """I'm moving my dictation app's dictionary and text shortcuts to a new app called Voice Bubble. From the screenshots I'm attaching (or the list below), write ALL of them out in exactly this format, inside one code block, with nothing else:

word: <a dictionary word or name, spelled exactly as shown>
short: <what I say> => <the text it should write>

One entry per line. Keep spelling and capitalisation exactly as in the screenshots. If a shortcut's text has several lines, write \n where a new line starts. Skip buttons, headings and anything that isn't an entry."""
}
