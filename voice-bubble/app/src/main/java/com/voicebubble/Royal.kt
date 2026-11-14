package com.voicebubble

/**
 * Royal English: you talk like a sophisticated English royal of the 1500s
 * (the royal "We", thou/thee/thy, "Verily…"). [apply] is the instant version, on the phone with
 * no AI; [AI] is what the AI is told to do.
 */
object Royal {
    /** How the mode is named in the app ("Talking like a 1500s royal"). */
    const val NAME = "a 1500s royal"

    /** The instruction for the AI version. (No length words in it: those come from the length setting.) */
    const val AI = "Make me sound like a sophisticated English royal of the 1500s. " +
        "Use elevated, ornate Early Modern English, not quotes or lines from plays: the royal 'We', 'Us' and 'Our' " +
        "for myself; 'thou', 'thee' and 'thy' for the listener; words such as hark, pray, prithee, forsooth, " +
        "verily, mayhap, wherefore, henceforth and it pleaseth Us. Grand, refined and gracious. " +
        "Don't call anyone a peasant or a subject, or talk down to them: speak to them as you would to anyone. " +
        "Keep what I meant and keep any names."

    // Order matters: longer phrases first, "I"/"me"/"my" last.
    private val swaps: List<Pair<String, String>> = listOf(
        // The royal "We".
        "I am" to "We are", "I'm" to "We are", "am I" to "are We", "I was" to "We were", "was I" to "were We",
        "I've" to "We have", "I'll" to "We shall", "I will" to "We shall", "I'd" to "We would",
        "I want" to "We desire", "I need" to "We require", "I think" to "We deem",
        "give me" to "bring unto Us", "tell me" to "inform Us", "let me know" to "inform Us", "let me" to "permit Us to",
        "my house" to "Our castle", "your house" to "thy dwelling", "my friend" to "Our dear friend",
        // To the listener.
        "are you" to "art thou", "you are" to "thou art", "you're" to "thou art", "you were" to "thou wert",
        "do you" to "dost thou", "did you" to "didst thou", "have you" to "hast thou", "you have" to "thou hast",
        "can you" to "canst thou", "will you" to "wilt thou", "would you" to "wouldst thou", "you will" to "thou wilt",
        "it is" to "'tis", "it's" to "'tis", "it was" to "'twas", "don't" to "do not", "doesn't" to "doth not",
        "can't" to "cannot", "won't" to "shall not", "isn't" to "is not", "aren't" to "art not",
        "thank you" to "We thank thee", "thanks" to "We thank thee", "goodbye" to "fare thee well",
        "good morning" to "good morrow", "right now" to "at once", "of course" to "naturally, as We decree",
        "shut up" to "hold thy tongue", "come here" to "approach Us", "go away" to "begone",
        "your" to "thy", "yours" to "thine", "yourself" to "thyself", "you" to "thee",
        "does" to "doth", "has" to "hath", "yes" to "aye", "yeah" to "aye", "hello" to "good morrow",
        "hi" to "good morrow", "hey" to "hark", "bye" to "fare thee well", "please" to "pray",
        "sorry" to "pardon Us", "maybe" to "mayhap", "perhaps" to "mayhap", "really" to "verily", "truly" to "verily",
        "very" to "most", "before" to "ere", "often" to "oft", "why" to "wherefore", "nothing" to "naught",
        "anything" to "aught", "okay" to "very well", "ok" to "very well", "lol" to "how droll", "lmao" to "how droll",
        "want" to "desire", "wants" to "desires", "need" to "require", "needs" to "requires", "think" to "deem",
        "help" to "aid", "buy" to "purchase", "hate" to "abhor", "kid" to "young one", "kids" to "young ones",
        "everyone" to "all assembled", "everybody" to "all assembled", "guys" to "good people",
        "guy" to "good sir", "dude" to "good sir", "bro" to "good sir", "boss" to "liege",
        "friend" to "companion", "friends" to "companions", "girl" to "maiden", "girls" to "maidens",
        "mom" to "Our royal mother", "dad" to "Our royal father", "food" to "victuals", "money" to "gold",
        "car" to "carriage", "phone" to "talking-glass", "cool" to "most splendid", "awesome" to "most splendid",
        "amazing" to "most splendid", "bad" to "most foul", "stupid" to "witless", "idiot" to "knave",
        "soon" to "anon", "listen" to "hark", "kill" to "slay", "fight" to "duel",
        "myself" to "Ourselves", "my" to "Our", "mine" to "Ours", "me" to "Us", "I" to "We",
    )
    private val rules = swaps.map { (from, to) ->
        Regex("(?<![\\p{L}'])" + from.split(" ").joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}'])", RegexOption.IGNORE_CASE) to to
    }
    private val opener = Regex("^(?i)(hark|pray|nay|aye|good morrow|fare thee well|we thank thee|hear ye|forsooth|verily|hold thy tongue|begone|approach)\\b")

    fun apply(text: String): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        for ((re, to) in rules) {
            t = re.replace(t) { m ->
                // Keep a capital letter where there was one.
                if (m.value[0].isUpperCase()) to.replaceFirstChar { it.uppercaseChar() } else to
            }
        }
        // "No" as an answer (at the start of a sentence) is "Nay"; "no gold" stays.
        t = Regex("(^|[.!?]\\s+)[Nn]o\\b(?=[,.!?]|\\s*$)").replace(t) { it.groupValues[1] + "Nay" }
        // "you" as the one doing something is "thou": at the start of a sentence, "thee" becomes "thou".
        t = Regex("(^|[.!?]\\s+)Thee\\b").replace(t) { it.groupValues[1] + "Thou" }
        // A royal opening (not when it already starts royally).
        if (!opener.containsMatchIn(t)) {
            val end = t.indexOfFirst { it in ".!?" }
            t = if (end >= 0 && t[end] == '?') {
                "Pray tell, " + if (Regex("^(We|Us|Our|Ours)\\b").containsMatchIn(t)) t else t.replaceFirstChar { it.lowercaseChar() }
            } else "Verily, " + if (Regex("^(We|Us|Our|Ours|I)\\b").containsMatchIn(t)) t else t.replaceFirstChar { it.lowercaseChar() }
        }
        return t
    }

    /** Asked for this style ("make me sound like a royal", the old "Shakespeare", "medieval English"…). */
    fun wanted(instructionOrPersona: String?) = instructionOrPersona != null &&
        Regex("(?i)shakespear|royal|medieval|elizabethan|old english|1500|fifteen hundred|monarch").containsMatchIn(instructionOrPersona)

    /** What the AI is told for "make me sound like …". */
    fun instruction(who: String, c: android.content.Context? = null) =
        if (wanted(who) && !Characters.isMix(who)) AI else Characters.instruction(who, c)
}
