package com.voicebubble

import kotlin.random.Random

/**
 * Characters on the phone with no AI: instant, but simpler than the AI versions. Each one changes your words
 * the way that character talks (Spamton's [[brackets]] and CAPS, Papyrus's capitals, Sans's lowercase…).
 */
object Instant {
    /** Characters with an instant version. */
    val supported = listOf("Spamton", "Rouxls Kaard", "Papyrus", "Sans", "Temmie", "Jevil", "Lancer", "Undyne", "Flowey")

    private fun key(who: String): String? {
        val n = who.lowercase()
        return when {
            "spamton" in n -> "spamton"
            "rouxls" in n || "rules card" in n -> "rouxls"
            "papyrus" in n -> "papyrus"
            n.trim() == "sans" || n.startsWith("sans ") -> "sans"
            "temmie" in n -> "temmie"
            "jevil" in n -> "jevil"
            "lancer" in n -> "lancer"
            "undyne" in n -> "undyne"
            "flowey" in n || "flowery" in n -> "flowey"
            else -> null
        }
    }

    fun has(who: String?) = who != null && key(who) != null

    private val small = setOf("the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at", "for", "is", "it",
        "i", "you", "me", "my", "we", "be", "so", "do", "if", "up", "can", "are", "was", "this", "that", "with")

    private val ads = listOf("[[Special Deal]]", "[[Limited Time Offer]]", "[[BIG SHOT]]", "[[Hyperlink Blocked]]",
        "[[Number One Rated Salesman]]", "[[Click Here]]", "[[Free Trial]]", "[[Cheap]]")

    /** Your words as [who] would say them, or null when there's no instant version. */
    fun apply(who: String, text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return t
        val rnd = Random(t.hashCode())   // the same message always comes out the same
        return when (key(who)) {
            "spamton" -> spamton(t, rnd)
            "rouxls" -> rouxls(t, rnd)
            "papyrus" -> "${t.uppercase().replace(Regex("\\bI AM\\b|\\bI'M\\b"), "THE GREAT PAPYRUS IS")} NYEH HEH HEH!"
            "sans" -> "heh. " + t.lowercase().trimEnd('.', '!') + ", kid."
            "temmie" -> temmie(t)
            "jevil" -> "UEE HEE HEE! ${t.uppercase()} CHAOS, CHAOS!"
            "lancer" -> "Ho ho ho! Lancer here! $t"
            "undyne" -> "NGAHHH!! ${t.uppercase().trimEnd('.', '!')}, PUNK!!"
            "flowey" -> flowey(t)
            else -> null
        }
    }

    private fun words(t: String) = Regex("[\\p{L}']+").findAll(t).toList()

    private fun spamton(t: String, rnd: Random): String {
        // A couple of words become [[Ad Phrases]], some go CAPS, and a sales pitch around it.
        val ws = words(t).filter { it.value.length >= 4 && it.value.lowercase() !in small }
        val bracket = ws.shuffled(rnd).take(2).map { it.range.first }.toSet()
        val sb = StringBuilder()
        var last = 0
        for (m in words(t)) {
            sb.append(t, last, m.range.first)
            val w = m.value
            sb.append(when {
                m.range.first in bracket -> "[[" + w.replaceFirstChar { it.uppercaseChar() } + "]]"
                rnd.nextInt(3) == 0 -> w.uppercase()
                else -> w
            })
            last = m.range.last + 1
        }
        sb.append(t.substring(last))
        val body = sb.toString().trimEnd('.', '!')
        return "HEY   EVERY  BUDDY!! $body!! ${ads[rnd.nextInt(ads.size)]}"
    }

    private fun rouxls(t: String, rnd: Random): String {
        // "you" → thou/thee/thy, "-eth" stuck on some words, Random Capitals, and "Worm".
        var s = t.replace(Regex("(?i)\\byour\\b"), "thy").replace(Regex("(?i)\\byou're\\b"), "thou art")
            .replace(Regex("(?i)\\byou\\b"), "thou")
        val sb = StringBuilder()
        var last = 0
        var n = 0
        for (m in words(s)) {
            sb.append(s, last, m.range.first)
            var w = m.value
            if (w.length >= 4 && w.lowercase() !in small && w.all { it.isLetter() }) {
                n++
                if (n % 3 == 1) w = w + "eth"
                if (rnd.nextInt(2) == 0) w = w.replaceFirstChar { it.uppercaseChar() }
            }
            sb.append(w)
            last = m.range.last + 1
        }
        sb.append(s.substring(last))
        s = sb.toString().trimEnd('.', '!')
        return "Hark, Worm!! $s!!"
    }

    private fun temmie(t: String): String {
        var s = t.replace(Regex("(?i)\\bI'm\\b|\\bI am\\b"), "tem is").replace(Regex("\\bI\\b"), "tem")
            .replace(Regex("(?i)\\bmy\\b"), "tem's")
        // Every third longer word gets its second half in capitals ("coLLEGE").
        var n = 0
        s = Regex("[\\p{L}]{5,}").replace(s) { m ->
            n++
            if (n % 3 == 1) m.value.substring(0, m.value.length / 2) + m.value.substring(m.value.length / 2).uppercase() else m.value
        }
        return "hOI!! " + s.trimEnd('.', '!') + "!!"
    }

    private fun flowey(t: String): String {
        // One word STRESSED, a friendly start and a creepy giggle.
        val ws = words(t).filter { it.value.length >= 4 && it.value.lowercase() !in small }
        var s = t
        ws.maxByOrNull { it.value.length }?.let { m -> s = s.replaceRange(m.range, m.value.uppercase()) }
        return "Howdy! " + s.trimEnd('.') + ". Hee hee hee."
    }
}
