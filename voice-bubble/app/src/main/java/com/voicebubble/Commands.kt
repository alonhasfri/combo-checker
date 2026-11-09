package com.voicebubble

/**
 * Spoken commands. Anything that starts with the trigger phrase ("out of context") is a command
 * for Voice Bubble, not text to type:
 *   "out of context, enable numbers and dates" / "…disable filler remover"
 *   "out of context, rewrite, make this more polite" / "…make this shorter"
 *   "out of context, translate to Spanish" / "…stop translating"
 *   "out of context, reply" / "…reply saying I'll be there at 6"
 *   "out of context, undo"
 */
object Commands {

    enum class Feature(val title: String, val words: List<String>) {
        COMMANDS("Voice commands", listOf("voice commands", "commands", "punctuation commands")),
        EMOJI("Emoji by voice", listOf("emoji", "emojis", "emoji by voice")),
        FILLER("Filler remover", listOf("filler remover", "filler removal", "filler words", "fillers", "filler")),
        NUMBERS("Numbers and dates", listOf("numbers and dates", "numbers", "dates", "number")),
        CAPS("Caps lock", listOf("caps lock", "caps", "capital letters", "all caps")),
        ONLY_TYPING("Only while typing", listOf("only while typing", "hide when not typing")),
        EXTRA("Extra accuracy", listOf("extra accuracy", "accuracy")),
        SOUNDS("Sounds", listOf("sounds", "sound", "sound effects")),
        VIBRATION("Vibration", listOf("vibration", "vibrate", "haptics")),
        PACK_JJK("Jujutsu Kaisen words", listOf("jjk", "jujutsu kaisen", "jujutsu", "jjk mode", "jjk pack")),
        PACK_TOBY("Deltarune & Undertale words", listOf("deltarune", "undertale", "deltarune and undertale", "toby fox")),
        PACK_GENZ("Gen Z slang words", listOf("gen z", "gen z slang", "slang", "genz")),
    }

    sealed class Cmd {
        data class Toggle(val feature: Feature, val on: Boolean) : Cmd()
        data class Rewrite(val instruction: String) : Cmd()
        data class Translate(val language: String?) : Cmd()   // null = stop translating
        data class Reply(val instruction: String) : Cmd()
        data class Summarize(val instruction: String) : Cmd()
        /** Rewrite everything from now on to sound like someone (null = back to normal). */
        data class Persona(val who: String?) : Cmd()
        /** Royal English on: instant (on the phone), the AI version, or null = whichever you used last. */
        data class RoyalMode(val instant: Boolean?) : Cmd()
        /** How long AI text should be from now on: "short", "medium", "long", or "" for as you said it. */
        data class Length(val length: String) : Cmd()
        object Undo : Cmd()
        data class Unknown(val said: String) : Cmd()
    }

    /** "scratch that" and friends: remove what was just said. */
    const val SCRATCH = "scratch that|scrap that|strike that|delete that|remove that|forget that|cancel that|erase that"

    /** The whole recording is just "scratch that": undo the last dictation. */
    fun isScratch(text: String): Boolean =
        Regex("^(?:no |oops |wait )?(?:$SCRATCH)$").matches(norm(text))

    const val DEFAULT_WORD = "out of context"

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** A word in the text, with where it is. */
    private class Tok(val start: Int, val end: Int, val word: String)
    private fun toks(text: String) =
        Regex("[\\p{L}\\p{N}'’]+").findAll(text).map { Tok(it.range.first, it.range.last + 1, it.value) }.toList()

    /** Two words sound alike: small differences allowed ("bubbles"/"bubble", "hay"/"hey", "contacts"/"context"). */
    private fun alike(heard: String, want: String): Boolean {
        val a = Words.sound(heard).removeSuffix("s")
        val b = Words.sound(want).removeSuffix("s")
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        val len = maxOf(a.length, b.length)
        // Longer words must start the same ("bubble" ≠ "double").
        if (len >= 4 && a[0] != b[0]) return false
        val allowed = if (len <= 3) 1 else maxOf(1, len / 3)
        return Words.lev(a, b) <= allowed
    }

    /**
     * Where the command phrase is: the token index range, matched by sound word by word.
     * The first word of a longer phrase may be any short word ("hey" heard as "a" or "hi").
     */
    private fun phraseAt(t: List<Tok>, i: Int, phrase: List<String>): Boolean {
        if (i + phrase.size > t.size) return false
        return phrase.indices.all { j ->
            val heard = t[i + j].word
            alike(heard, phrase[j]) || (j == 0 && phrase.size > 1 && phrase[0].length <= 3 && heard.length <= 3)
        }
    }

    private fun phraseWords(word: String) = norm(word).ifEmpty { DEFAULT_WORD }.split(' ').filter { it.isNotEmpty() }

    /** A command, plus what you dictated before it (empty when the recording starts with the command). */
    data class Split(val before: String, val cmd: Cmd)

    /**
     * Finds a command anywhere in the dictation. Said after some text, it must be a real command,
     * so a normal sentence like "that quote was out of context" is still typed as text.
     */
    fun split(text: String, word: String = DEFAULT_WORD): Split? {
        val phrase = phraseWords(word)
        val t = toks(text)
        for (i in t.indices) {
            if (!phraseAt(t, i, phrase)) continue
            val before = text.substring(0, t[i].start).trim().trimEnd(',', ';', ':', '-', '.').trim()
            val cmd = parseRest(text.substring(t[i + phrase.size - 1].end).trimStart(' ', ',', '.', ':', ';', '!', '-').trim())
            // After some text it must be a real command, so normal sentences aren't swallowed.
            if (before.isNotEmpty() && cmd is Cmd.Unknown) continue
            return Split(before, cmd)
        }
        return null
    }

    /**
     * "short", "short mode", "keep it short", "long answers", "medium length", "normal length".
     * ("make this shorter" stays a one-off rewrite.)
     */
    private fun length(n: String): String? {
        Regex("^(?:keep (?:it|them|everything|things|my messages|messages|replies|answers) |always |use |switch to |go |set (?:it |length |the length )?to )?" +
            "(short|shorter|brief|medium|middle|normal|regular|long|longer|any|no)" +
            "(?: (?:mode|length|size|answers|replies|messages|ones|texts?))?(?: from now on| please)?$").find(n)?.let { m ->
            val word = m.groupValues[1]
            val plain = m.value == word
            // Bare "normal" means "talk normally", not a length; "any"/"no" only with "length".
            if (word in setOf("normal", "regular", "any", "no") && !Regex("\\b(length|size)\\b").containsMatchIn(n)) return null
            if (plain && word in setOf("shorter", "longer")) return null
            return when (word) {
                "short", "shorter", "brief" -> "short"
                "medium", "middle" -> "medium"
                "long", "longer" -> "long"
                else -> ""
            }
        }
        Regex("^(?:length|size) (short|medium|long|normal|off|any)$").find(n)?.let { m ->
            return when (val w = m.groupValues[1]) { "short", "medium", "long" -> w; else -> "" }
        }
        if (Regex("^(?:stop|turn off|disable|no more) (?:short|long|medium) (?:mode|length|answers)$").matches(n)) return ""
        return null
    }

    /** Null when it's normal dictation (command at the start only; "um"/"okay" before it is fine). */
    fun parse(text: String, word: String = DEFAULT_WORD): Cmd? {
        val phrase = phraseWords(word)
        val t = toks(text)
        val start = when {
            phraseAt(t, 0, phrase) -> 0
            t.isNotEmpty() && t[0].word.lowercase() in setOf("um", "uh", "okay", "ok", "so", "and") && phraseAt(t, 1, phrase) -> 1
            else -> return null
        }
        return parseRest(text.substring(t[start + phrase.size - 1].end).trimStart(' ', ',', '.', ':', ';', '!', '-').trim())
    }

    private fun parseRest(rest: String): Cmd {
        val n = norm(rest)

        Regex("^(?:please )?(enable|turn on|switch on|activate|start|disable|turn off|switch off|deactivate|stop) (?:the )?(.+?)(?: mode| pack| words)?$")
            .find(n)?.let { mm ->
                val on = mm.groupValues[1] in setOf("enable", "turn on", "switch on", "activate", "start")
                val what = mm.groupValues[2]
                if (what == "translating" || what == "translation") return Cmd.Translate(null)
                feature(what)?.let { return Cmd.Toggle(it, on) }
            }
        // "talk normally" / "stop talking like Shakespeare": persona off.
        if (Regex("^(?:(?:make|let) (?:me|it|this) )?(?:talk|speak|sound|be|write) normal(?:ly)?|^(?:go )?back to normal|" +
                "^normal(?: mode| voice| me)?$|^stop (?:talking|sounding|speaking|writing) like|" +
                "^stop (?:the )?(?:character|persona|voice|accent|shakespeare|royal|medieval)|^no more (?:persona|character|shakespeare|royal|medieval)|" +
                "^(?:turn off|disable) (?:the )?(?:character|persona|talk like)|^be (?:myself|me)\\b|^my (?:own|normal) voice").containsMatchIn(n))
            return Cmd.Persona(null)
        length(n)?.let { return Cmd.Length(it) }
        // "royal mode" / "offline royal" / "switch to AI royal" (and the old "offline Shakespeare"): Royal English.
        Regex("^(?:(?:switch|change|go) (?:to )?|use |turn on |enable |start )?(?:the )?" +
            "(?:(instant|offline|fast|quick|simple|ai|a i|online|smart|better|full|real|good|normal) )?" +
            "(?:shakespeare|royal|medieval|old english)(?: english)?(?: mode| version| one| talk)?$")
            .find(n)?.let { mm ->
                val q = mm.groupValues[1]
                return Cmd.RoyalMode(if (q.isEmpty()) null else q in setOf("instant", "offline", "fast", "quick", "simple"))
            }
        // "make me sound like Shakespeare", "talk like Rouxls Kaard from now on", "in the style of a pirate".
        Regex("^(?:make (?:me|it|this) (?:sound|talk|speak|write|look) like|make (?:me|it|this) (?:sound|talk|speak) as|sound like|talk like|speak like|write like|say it like|say this like|rewrite (?:it |this )?(?:like|as)|in the style of|(?:always )?(?:talk|sound|speak) like)\\s+(.+)$")
            .find(n)?.let { mm ->
                val lasting = Regex("\\b(from now on|for now|until i say|always|mode)\\b").containsMatchIn(n) || n.startsWith("always")
                // The name as you said it (keeps capitals), without "from now on".
                val who = rest.replace(Regex("^(?i).*?\\blike\\s+|^(?i)in the style of\\s+"), "")
                    .replace(Regex("(?i)[\\s,]*(from now on|for now|until i say.*|mode|always)[.!]*$"), "")
                    .trim().trimEnd('.', '!', ',').ifEmpty { mm.groupValues[1] }
                // Shakespeare, medieval, royal… all mean Royal English now.
                val named = if (Royal.wanted(who)) Royal.NAME else who
                return if (lasting) Cmd.Persona(named) else Cmd.Rewrite("Make me sound like $named")
            }
        if (n.startsWith("stop translating") || n == "translation off" || n == "no translation") return Cmd.Translate(null)
        Regex("^translate (?:it |this |everything )?(?:to|into) ([\\p{L} ]+)$").find(n)?.let { return Cmd.Translate(it.groupValues[1].trim()) }
        if (n == "undo" || n == "undo that" || isScratch(n)) return Cmd.Undo
        if (n.startsWith("rewrite") || n.startsWith("make this") || n.startsWith("make it")) {
            // Keep the original wording (with its punctuation) of the instruction.
            val instr = rest.replace(Regex("^(?i)rewrite[\\s,.:;!-]*(?:it|this)?[\\s,.:;!-]*"), "").trim().trimEnd('.')
            return Cmd.Rewrite(instr.ifEmpty { "Make it clearer and better written" })
        }
        if (Regex("^(summarise|summarize|sum up|sum it up|tldr|tl dr|give me a summary|recap)\\b").containsMatchIn(n)) {
            val instr = rest.replace(Regex("^(?i)(?:summari[sz]e|sum (?:it )?up|tl;? ?dr|give me a summary|recap)[\\s,.:;!-]*(?:this chat|the chat|this|it)?[\\s,.:;!-]*"), "")
                .trim().trimEnd('.')
            return Cmd.Summarize(instr)
        }
        if (n.startsWith("reply") || n.startsWith("answer")) {
            val instr = rest.replace(Regex("^(?i)(?:reply|answer)[\\s,.:;!-]*"), "").trim().trimEnd('.')
            return Cmd.Reply(instr)
        }
        // Anything else that starts like an instruction ("turn this into a rap", "add more emojis")
        // is a rewrite request for the AI.
        if (Regex("^(make|turn|change|rewrite|fix|add|remove|put|use|write|talk|speak|say|shorten|lengthen|convert|reword|rephrase|improve|translate|correct|expand|simplify|format)\\b").containsMatchIn(n))
            return Cmd.Rewrite(rest.trim().trimEnd('.'))
        return Cmd.Unknown(rest)
    }

    fun feature(said: String): Feature? {
        val s = norm(said)
        return Feature.values().firstOrNull { f -> f.words.any { it == s } }
            ?: Feature.values().firstOrNull { f -> f.words.any { w -> Regex("\\b" + Regex.escape(w) + "\\b").containsMatchIn(s) } }
    }
}
