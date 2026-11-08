package com.voicebubble

/**
 * Instant Shakespeare, on the phone with no AI: swaps everyday words for Shakespeare's
 * (thou, thee, thy, art, doth, hath, aye, nay, prithee…) and opens with "Forsooth,".
 * Simpler than the AI version, but there's no waiting.
 */
object Shakespeare {
    // Order matters: longer phrases first.
    private val swaps: List<Pair<String, String>> = listOf(
        "are you" to "art thou", "you are" to "thou art", "you're" to "thou art", "you were" to "thou wert",
        "do you" to "dost thou", "did you" to "didst thou", "have you" to "hast thou", "you have" to "thou hast",
        "can you" to "canst thou", "will you" to "wilt thou", "would you" to "wouldst thou", "you will" to "thou wilt",
        "it is" to "'tis", "it's" to "'tis", "it was" to "'twas", "don't" to "do not", "doesn't" to "doth not",
        "can't" to "cannot", "won't" to "shall not", "isn't" to "is not", "aren't" to "art not",
        "thank you" to "I thank thee", "thanks" to "I thank thee", "goodbye" to "fare thee well",
        "good morning" to "good morrow", "right now" to "anon", "of course" to "forsooth",
        "your" to "thy", "yours" to "thine", "yourself" to "thyself", "you" to "thee",
        "does" to "doth", "has" to "hath", "yes" to "aye", "yeah" to "aye", "hello" to "good morrow",
        "hi" to "good morrow", "hey" to "hark", "bye" to "fare thee well", "please" to "prithee",
        "maybe" to "perchance", "perhaps" to "perchance", "really" to "verily", "truly" to "verily",
        "very" to "most", "before" to "ere", "often" to "oft", "why" to "wherefore", "nothing" to "naught",
        "anything" to "aught", "okay" to "very well", "ok" to "very well", "girl" to "maiden", "girls" to "maidens",
        "guy" to "good sir", "dude" to "good sir", "bro" to "good sir", "man" to "good sir", "mom" to "mother",
        "dad" to "father", "food" to "victuals", "cool" to "most wondrous", "awesome" to "most wondrous",
        "amazing" to "most wondrous", "bad" to "most foul", "stupid" to "foolish", "idiot" to "knave",
        "think" to "bethink", "soon" to "anon", "go away" to "begone", "come here" to "come hither",
        "listen" to "hark", "kill" to "slay", "fight" to "duel", "money" to "coin", "phone" to "talking-glass",
    )
    private val rules = swaps.map { (from, to) ->
        Regex("(?<![\\p{L}'])" + from.split(" ").joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}'])", RegexOption.IGNORE_CASE) to to
    }

    fun apply(text: String): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        for ((re, to) in rules) {
            t = re.replace(t) { m ->
                // Keep a capital letter where there was one.
                if (m.value[0].isUpperCase()) to.replaceFirstChar { it.uppercaseChar() } else to
            }
        }
        // "No" as an answer (at the start of a sentence) is "Nay"; "no money" stays.
        t = Regex("(^|[.!?]\\s+)[Nn]o\\b(?=[,.!?]|\\s*$)").replace(t) { it.groupValues[1] + "Nay" }
        // "you" as the one doing something is "thou": at the start of a sentence, "thee" becomes "thou".
        t = Regex("(^|[.!?]\\s+)Thee\\b").replace(t) { it.groupValues[1] + "Thou" }
        // Open with a flourish (not when it already starts like Shakespeare).
        val opener = Regex("^(?i)(forsooth|good morrow|aye|nay|prithee|hark|verily|fare thee well|I thank thee)\\b")
        if (!opener.containsMatchIn(t)) {
            t = "Forsooth, " + if (t.startsWith("I ") || t.startsWith("I'")) t else t.replaceFirstChar { it.lowercaseChar() }
        }
        return t
    }

    /** Asked for Shakespeare ("make me sound like Shakespeare", Talk like → Shakespeare). */
    fun wanted(instructionOrPersona: String?) = instructionOrPersona?.contains("shakespeare", ignoreCase = true) == true
}
