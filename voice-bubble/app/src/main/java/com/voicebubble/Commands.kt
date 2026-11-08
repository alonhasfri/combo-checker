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
        object Undo : Cmd()
        data class Unknown(val said: String) : Cmd()
    }

    /** "scratch that" and friends: remove what was just said. */
    const val SCRATCH = "scratch that|scrap that|strike that|delete that|remove that|forget that|cancel that|erase that"

    /** The whole recording is just "scratch that": undo the last dictation. */
    fun isScratch(text: String): Boolean =
        Regex("^(?:no |oops |wait )?(?:$SCRATCH)$").matches(norm(text))

    const val DEFAULT_WORD = "out of context"

    /** The command phrase as a pattern. The default also allows how Whisper tends to mishear it. */
    private fun phrase(word: String): String {
        val w = norm(word).ifEmpty { DEFAULT_WORD }
        if (w == DEFAULT_WORD) return "(?:hey\\s+)?out\\s+of\\s+(?:context|contacts|contexts|content|contest)"
        return w.split(' ').joinToString("[\\s,.-]+") { Regex.escape(it) }
    }

    private fun trigger(word: String) = Regex("^\\s*" + phrase(word) + "\\b[\\s,.:;!-]*", RegexOption.IGNORE_CASE)

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** "…text…, out of context, make this longer": the phrase can also come after what you dictated. */
    private fun triggerAnywhere(word: String) =
        Regex("(?:^|[\\s,.!?;:]+)" + phrase(word) + "\\b[\\s,.:;!-]*", RegexOption.IGNORE_CASE)

    /** A command, plus what you dictated before it (empty when the recording starts with the command). */
    data class Split(val before: String, val cmd: Cmd)

    /**
     * Finds a command anywhere in the dictation. Said after some text, it must be a real command,
     * so a normal sentence like "that quote was out of context" is still typed as text.
     */
    fun split(text: String, word: String = DEFAULT_WORD): Split? {
        val m = triggerAnywhere(word).find(text) ?: return null
        val before = text.substring(0, m.range.first).trim().trimEnd(',', ';', ':', '-').trim()
        val cmd = parseRest(text.substring(m.range.last + 1).trim())
        if (before.isNotEmpty() && cmd is Cmd.Unknown) return null
        return Split(before, cmd)
    }

    /** Null when it's normal dictation (command at the start only). */
    fun parse(text: String, word: String = DEFAULT_WORD): Cmd? {
        val m = trigger(word).find(text) ?: return null
        return parseRest(text.substring(m.range.last + 1).trim())
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
        return Cmd.Unknown(rest)
    }

    fun feature(said: String): Feature? {
        val s = norm(said)
        return Feature.values().firstOrNull { f -> f.words.any { it == s } }
            ?: Feature.values().firstOrNull { f -> f.words.any { w -> Regex("\\b" + Regex.escape(w) + "\\b").containsMatchIn(s) } }
    }
}
