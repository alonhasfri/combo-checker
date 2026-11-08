package com.voicebubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Getting better the more you use it, all on the phone:
 *  - fixes: when you correct a mishearing ("Go Joe" → "Gojo") and tap ✓, that exact mistake is fixed from then on
 *  - your words: names you keep using are noticed and given to Whisper as hints
 *  - your style: a little of your recent text is given to Whisper as an example of how you write
 * (Retraining Whisper itself on your voice isn't possible on a phone.)
 */
object Adapt {
    private fun sp(c: Context) = c.getSharedPreferences("adapt", Context.MODE_PRIVATE)

    fun isOn(c: Context) = sp(c).getBoolean("on", true)
    fun setOn(c: Context, on: Boolean) = sp(c).edit().putBoolean("on", on).apply()

    // ---------------- remembered fixes ----------------

    fun fixes(c: Context): List<Shortcut> = try {
        val a = JSONArray(sp(c).getString("fixes", "[]"))
        List(a.length()) { a.getJSONObject(it).let { o -> Shortcut(o.getString("heard"), o.getString("meant")) } }
    } catch (e: Exception) { emptyList() }

    private fun saveFixes(c: Context, list: List<Shortcut>) {
        val a = JSONArray()
        list.takeLast(300).forEach { a.put(JSONObject().put("heard", it.say).put("meant", it.write)) }
        sp(c).edit().putString("fixes", a.toString()).apply()
    }

    fun addFix(c: Context, heard: String, meant: String) =
        saveFixes(c, fixes(c).filter { !it.say.equals(heard, true) } + Shortcut(heard, meant))

    fun removeFix(c: Context, f: Shortcut) = saveFixes(c, fixes(c).filter { it != f })

    /** Applies remembered fixes: the exact misheard words become what you meant. */
    fun applyFixes(c: Context, text: String, capitalize: Boolean): String =
        if (!isOn(c)) text else Words.applyShortcuts(text, fixes(c), capitalize)

    // ---------------- your words ----------------

    private val common = setOf(
        "i", "i'm", "i've", "i'll", "i'd", "the", "a", "an", "and", "but", "or", "so", "ok", "okay", "yes", "no",
        "hey", "hi", "hello", "thanks", "thank", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
        "sunday", "january", "february", "march", "april", "may", "june", "july", "august", "september",
        "october", "november", "december", "english", "god", "mom", "dad", "lol", "omg", "tbh", "btw",
    )

    /** Capitalised words in the middle of sentences (names, places, games…) you've said at least [min] times. */
    fun learnedWords(c: Context, min: Int = 3): List<String> = try {
        val o = JSONObject(sp(c).getString("words", "{}"))
        o.keys().asSequence().filter { o.optInt(it) >= min }.sortedByDescending { o.optInt(it) }.take(40).toList()
    } catch (e: Exception) { emptyList() }

    /** Words that look like names: capitalised, not at the start of a sentence, not everyday words. */
    fun nameCandidates(text: String): List<String> {
        val out = ArrayList<String>()
        val re = Regex("(?<![.!?]\\s)\\b(\\p{Lu}[\\p{Ll}'’-]{2,}(?:\\s+\\p{Lu}[\\p{Ll}'’-]{2,})?)\\b")
        for (m in re.findAll(text)) {
            val w = m.groupValues[1]
            if (m.range.first == 0) continue
            if (w.lowercase() in common) continue
            out += w
        }
        return out
    }

    /** Called with what Whisper heard after each dictation. */
    fun observe(c: Context, said: String) {
        if (!isOn(c)) return
        val names = nameCandidates(said)
        val p = sp(c)
        p.edit().putString("recent", (p.getString("recent", "")!! + " " + said).takeLast(400).trim()).apply()
        if (names.isEmpty()) return
        val o = try { JSONObject(p.getString("words", "{}")) } catch (e: Exception) { JSONObject() }
        names.forEach { o.put(it, o.optInt(it) + 1) }
        // Keep it small: drop the least used when it grows.
        if (o.length() > 300) {
            val keep = o.keys().asSequence().sortedByDescending { o.optInt(it) }.take(200).toSet()
            o.keys().asSequence().toList().filter { it !in keep }.forEach { o.remove(it) }
        }
        p.edit().putString("words", o.toString()).apply()
    }

    /** Extra hint for Whisper: your frequent names, then a bit of how you've been writing lately. */
    fun prompt(c: Context): String {
        if (!isOn(c)) return ""
        val dict = Words.dictionary(c).map { it.lowercase() }.toSet()
        val names = learnedWords(c).filter { it.lowercase() !in dict }.joinToString(", ")
        val recent = sp(c).getString("recent", "")!!.takeLast(160).substringAfter(' ').trim()
        return listOf(names, recent).filter { it.isNotBlank() }.joinToString(". ").take(320)
    }

    fun forget(c: Context) = sp(c).edit().remove("words").remove("recent").remove("fixes").apply()
}
