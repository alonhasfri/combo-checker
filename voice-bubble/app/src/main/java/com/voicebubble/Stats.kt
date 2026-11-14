package com.voicebubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Numbers for the Home dashboard and the list of recent dictations. All kept on the phone. */
object Stats {
    private fun sp(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    /** Typing speed on a phone keyboard, used for "time saved". */
    private const val TYPING_WPM = 38.0

    class Entry(val text: String, val time: Long, val app: String?)

    fun wordCount(s: String) = s.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    private fun dayKey(t: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    /** Called after each successful dictation. */
    fun record(c: Context, text: String, spokenSeconds: Float, app: String?) {
        val words = wordCount(text)
        val p = sp(c)
        val today = dayKey()
        val sameDay = p.getString("day", "") == today
        val saved = maxOf(0.0, words / TYPING_WPM * 60.0 - spokenSeconds)
        // Consecutive days with at least one dictation.
        val yesterday = dayKey(System.currentTimeMillis() - 86_400_000L)
        val streak = when (p.getString("lastDay", "")) {
            today -> p.getInt("streak", 1)
            yesterday -> p.getInt("streak", 0) + 1
            else -> 1
        }
        p.edit()
            .putInt("total", p.getInt("total", 0) + 1)
            .putLong("words", p.getLong("words", 0) + words)
            .putFloat("savedSec", (p.getFloat("savedSec", 0f) + saved).toFloat())
            .putString("day", today)
            .putInt("todayWords", (if (sameDay) p.getInt("todayWords", 0) else 0) + words)
            .putInt("todayCount", (if (sameDay) p.getInt("todayCount", 0) else 0) + 1)
            .putString("lastDay", today)
            .putInt("streak", streak)
            .apply()
        if (historyOn(c)) {
            val arr = history(c).toMutableList()
            arr.add(0, Entry(text, System.currentTimeMillis(), app))
            val out = JSONArray()
            arr.take(500).forEach { out.put(JSONObject().put("t", it.text).put("at", it.time).put("app", it.app ?: "")) }
            p.edit().putString("history", out.toString()).apply()
        }
    }

    fun total(c: Context) = sp(c).getInt("total", 0)
    fun words(c: Context) = sp(c).getLong("words", 0)
    fun savedMinutes(c: Context) = (sp(c).getFloat("savedSec", 0f) / 60f).toInt()
    fun todayWords(c: Context) = if (sp(c).getString("day", "") == dayKey()) sp(c).getInt("todayWords", 0) else 0
    fun streak(c: Context): Int {
        val last = sp(c).getString("lastDay", "")
        val ok = last == dayKey() || last == dayKey(System.currentTimeMillis() - 86_400_000L)
        return if (ok) sp(c).getInt("streak", 0) else 0
    }

    fun historyOn(c: Context) = sp(c).getBoolean("historyOn", true)
    fun setHistoryOn(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("historyOn", on).apply()
        if (!on) clearHistory(c)
    }

    fun history(c: Context): List<Entry> = try {
        val a = JSONArray(sp(c).getString("history", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Entry(o.getString("t"), o.optLong("at"), o.optString("app").ifEmpty { null })
        }
    } catch (e: Exception) { emptyList() }

    fun clearHistory(c: Context) = sp(c).edit().putString("history", "[]").apply()

    fun deleteEntry(c: Context, e: Entry) {
        val out = JSONArray()
        history(c).filter { !(it.time == e.time && it.text == e.text) }
            .forEach { out.put(JSONObject().put("t", it.text).put("at", it.time).put("app", it.app ?: "")) }
        sp(c).edit().putString("history", out.toString()).apply()
    }

    /** "just now", "5 min ago", "3 h ago", "yesterday", "12 Nov". */
    fun ago(t: Long): String {
        val d = (System.currentTimeMillis() - t) / 1000
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86400 -> "${d / 3600} h ago"
            d < 172800 -> "yesterday"
            else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(t))
        }
    }
}
