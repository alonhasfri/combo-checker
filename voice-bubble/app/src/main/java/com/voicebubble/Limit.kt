package com.voicebubble

import android.content.Context

/**
 * Length limits, for chats with a maximum message size (Roblox chat: 200 characters).
 * AI results are asked to stay under it; anything still too long is cut between words, and the rest
 * is kept so you can paste it as the next message.
 */
object Limit {
    const val ROBLOX = "com.roblox.client"
    const val ROBLOX_CHARS = 200

    /** The limit for this app: your own setting for the app, Roblox's 200, or the general limit (null = none). */
    fun forApp(c: Context, pkg: String?): Int? =
        pkg?.let { Styles.appLimits(c)[it] }?.takeIf { it > 0 }
            ?: (if (pkg == ROBLOX && Styles.appLimits(c)[ROBLOX] == null) ROBLOX_CHARS else null)
            ?: Prefs(c).maxChars.takeIf { it > 0 }

    private val fillers = Regex("(?i)\\s*\\b(?:really|very|just|actually|basically|literally|totally|so so|kind of|sort of|you know|i mean|like,)\\b,?")
    private val contractions = listOf("I am" to "I'm", "do not" to "don't", "does not" to "doesn't", "is not" to "isn't",
        "are not" to "aren't", "can not" to "can't", "cannot" to "can't", "will not" to "won't", "I will" to "I'll",
        "you are" to "you're", "it is" to "it's", "that is" to "that's", "going to" to "gonna", "want to" to "wanna",
        "I would" to "I'd", "we are" to "we're", "they are" to "they're", "let us" to "let's")

    /**
     * One message that fits in [max] characters (never split): filler words go first, then long phrases are
     * contracted, and only then is it cut at the end of a sentence or a word.
     */
    fun squeeze(text: String, max: Int): String {
        if (max <= 0 || text.length <= max) return text
        var t = fillers.replace(text, "").replace(Regex("[ \\t]{2,}"), " ").trim()
        if (t.length > max) for ((a, b) in contractions) {
            t = Regex("(?i)\\b" + a.replace(" ", "\\s+") + "\\b").replace(t) { m -> if (m.value[0].isUpperCase()) b.replaceFirstChar { it.uppercaseChar() } else b }
        }
        if (t.length <= max) return t
        return fit(t, max).first.trimEnd(',', ';', ':', ' ', '-')
    }

    /** Splits [text] into what fits in [max] characters and the rest (null when it all fits). */
    fun fit(text: String, max: Int): Pair<String, String?> {
        if (max <= 0 || text.length <= max) return text to null
        val window = text.substring(0, max + 1)
        // Best: end of a sentence; then between words; otherwise a hard cut.
        val sentence = Regex("[.!?…](?=\\s)").findAll(window).lastOrNull()?.range?.last
        val cut = when {
            sentence != null && sentence >= max / 2 -> sentence + 1
            window.lastIndexOf(' ') >= max / 2 -> window.lastIndexOf(' ')
            else -> max
        }
        return text.substring(0, cut).trimEnd() to text.substring(cut).trim().ifEmpty { null }
    }
}
