package com.voicebubble

import android.content.Context

/**
 * Writing styles per kind of app: your chats can be casual while email stays formal.
 * All rule-based, on the phone. Built for English (other languages only get the caps/punctuation part).
 */
object Styles {

    enum class Category(val title: String, val where: String) {
        PERSONAL("Personal", "Applies in messaging apps"),
        WORK("Work", "Applies in work chat apps"),
        EMAIL("Email", "Applies in email apps"),
        OTHER("Other", "Applies everywhere else"),
    }

    enum class Style(val title: String, val subtitle: String) {
        FORMAL("Formal.", "Caps + Punctuation"),
        CASUAL("Casual", "Caps + Less punctuation"),
        VERY_CASUAL("very casual", "No caps + No punctuation"),
        EXCITED("Excited!", "Caps + Exclamation marks"),
    }

    /** Known apps per category (only installed ones are shown). Everything else is Other. */
    val apps: Map<Category, List<String>> = mapOf(
        Category.PERSONAL to listOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "org.telegram.messenger", "org.thunderdog.challegram", "com.facebook.orca", "org.thoughtcrime.securesms",
            "com.instagram.android", "com.snapchat.android", "com.discord", "jp.naver.line.android", "com.viber.voip",
            "com.facebook.mlite", "com.instagram.barcelona",
        ),
        Category.WORK to listOf(
            "com.Slack", "com.microsoft.teams", "com.google.android.apps.dynamite", "us.zoom.videomeetings",
            "com.mattermost.rn", "com.webex.meetings", "com.linkedin.android",
        ),
        Category.EMAIL to listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
            "ch.protonmail.android", "com.yahoo.mobile.client.android.mail", "com.readdle.spark", "com.fsck.k9",
            "net.thunderbird.android", "com.superhuman.mail",
        ),
    )

    fun categoryOf(pkg: String?): Category {
        if (pkg == null) return Category.OTHER
        return apps.entries.firstOrNull { pkg in it.value }?.key ?: Category.OTHER
    }

    val defaults = mapOf(
        Category.PERSONAL to Style.CASUAL,
        Category.WORK to Style.FORMAL,
        Category.EMAIL to Style.FORMAL,
        Category.OTHER to Style.FORMAL,
    )

    private fun sp(c: Context) = c.getSharedPreferences("voice", Context.MODE_PRIVATE)
    fun get(c: Context, cat: Category): Style =
        sp(c).getString("style_${cat.name}", null)?.let { n -> Style.values().firstOrNull { it.name == n } } ?: defaults.getValue(cat)
    fun set(c: Context, cat: Category, s: Style) = sp(c).edit().putString("style_${cat.name}", s.name).apply()

    /** Examples shown on the Styles screen (each style is applied to these live). */
    val samples = mapOf(
        Category.PERSONAL to "Hey, are you around tonight? I am thinking we could grab dinner at 7, if that works for you.",
        Category.WORK to "Hi team, the report is ready. I am sending it over now, so let me know what you think.",
        Category.EMAIL to "Hi Sarah, thanks so much for your help with the move. It is really appreciated. Talk soon. Best, Daniel",
        Category.OTHER to "Today I am going to the gym, and then I am meeting a friend for coffee.",
    )

    // ---------------- the rules ----------------

    fun apply(text: String, style: Style, cat: Category): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        return if (cat == Category.EMAIL) email(t, style) else body(t, style, keepGreetingComma = true)
    }

    private fun body(s: String, style: Style, keepGreetingComma: Boolean): String = when (style) {
        Style.FORMAL -> formal(s)
        Style.EXCITED -> excited(s)
        Style.CASUAL -> casual(s, keepGreetingComma)
        Style.VERY_CASUAL -> veryCasual(s)
    }

    private fun capFirst(s: String) = s.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

    fun formal(s: String): String {
        var t = capFirst(s.trim())
        if (t.isNotEmpty() && (t.last().isLetterOrDigit() || t.last() in "\"')")) t += "."
        return t
    }

    fun excited(s: String): String {
        val t = formal(s)
        return if (t.endsWith(".") && !t.endsWith("..")) t.dropLast(1) + "!" else t
    }

    private val greetingComma = Regex("^((?i:hi|hey|hello|dear|yo|hiya)(?:\\s+[\\p{L}'’-]+){0,2}),")
    private val numberComma = Regex("(?<=\\d),(?=\\d)")

    fun casual(s: String, keepGreetingComma: Boolean = true): String {
        var t = contract(capFirst(s.trim()))
        val greet = if (keepGreetingComma) greetingComma.find(t) else null
        val head = greet?.value ?: ""
        var rest = t.substring(head.length)
        rest = rest.replace(numberComma, "\u0001").replace(",", "").replace("\u0001", ",")
        t = (head + rest).replace(Regex("[ \\t]{2,}"), " ")
        if (t.endsWith(".") && !t.endsWith("..")) t = t.dropLast(1)
        return t.trimEnd()
    }

    fun veryCasual(s: String): String {
        var t = contract(s.trim()).lowercase()
        t = t.replace(Regex("(?<=\\d)\\.(?=\\d)"), "\u0001")   // keep 3.5
            .replace(numberComma, "\u0002")                     // keep 1,000
            .replace("…", " ")
            .replace(Regex("[.,;:!]+"), "")
            .replace("\u0001", ".")
            .replace("\u0002", ",")
            .replace(Regex("[ \\t]{2,}"), " ")
        return t.lines().joinToString("\n") { it.trim() }.trim()
    }

    // "I am going" -> "I'm going". Never at the end of a clause ("Yes, I am." stays).
    private val contractions = listOf(
        "i am" to "I'm", "you are" to "you're", "we are" to "we're", "they are" to "they're",
        "it is" to "it's", "that is" to "that's", "there is" to "there's", "what is" to "what's",
        "who is" to "who's", "he is" to "he's", "she is" to "she's", "let us" to "let's",
        "do not" to "don't", "does not" to "doesn't", "did not" to "didn't", "is not" to "isn't",
        "are not" to "aren't", "was not" to "wasn't", "were not" to "weren't", "cannot" to "can't",
        "can not" to "can't", "will not" to "won't", "would not" to "wouldn't", "should not" to "shouldn't",
        "could not" to "couldn't", "have not" to "haven't", "has not" to "hasn't",
        "i will" to "I'll", "you will" to "you'll", "we will" to "we'll", "they will" to "they'll",
        "it will" to "it'll",
    ).map { (from, to) ->
        Regex("\\b" + from.replace(" ", "\\s+") + "\\b(?=\\s+[\\p{L}\\d])", RegexOption.IGNORE_CASE) to to
    }

    fun contract(s: String): String {
        var t = s
        for ((re, to) in contractions) {
            t = re.replace(t) { m ->
                if (m.value[0].isUpperCase() && to[0] != 'I') to.replaceFirstChar { it.uppercaseChar() } else to
            }
        }
        return t
    }

    // ---------------- email layout ----------------

    private val greetingRe = Regex(
        "^((?i:hi|hello|hey|dear|good morning|good afternoon|good evening)" +
            "(?:(?:\\s+[\\p{L}'’-]+){0,3}(?=[,.!])|(?:\\s+(?!I\\b)\\p{Lu}[\\p{L}'’-]*){1,2}))[,.!]?\\s+"
    )
    private val signoffRe = Regex(
        "(?:^|(?<=[.!?,]\\s))((?i:best regards|kind regards|warm regards|regards|best wishes|all the best|" +
            "many thanks|thank you|thanks|cheers|sincerely|yours truly|best))[,.!]?\\s+" +
            "(\\p{Lu}[\\p{L}'’-]*(?:\\s+\\p{Lu}[\\p{L}'’-]*)?)[.!]?\\s*$"
    )

    fun email(text: String, style: Style): String {
        var rest = text.trim()
        var greeting: String? = null
        greetingRe.find(rest)?.let { m ->
            greeting = m.groupValues[1].trim()
            rest = rest.substring(m.range.last + 1)
        }
        var signoff: Pair<String, String>? = null
        signoffRe.find(rest)?.let { m ->
            signoff = capFirst(m.groupValues[1]) to m.groupValues[2]
            rest = rest.substring(0, m.range.first)
        }
        val veryCasual = style == Style.VERY_CASUAL
        var body = rest.trim()
        if (body.isNotEmpty()) body = body(body, style, keepGreetingComma = false)
        val sb = StringBuilder()
        greeting?.let { g ->
            val gg = if (veryCasual) g.lowercase() else capFirst(g)
            if (style == Style.FORMAL || style == Style.EXCITED) {
                sb.append(gg).append(",\n\n")
            } else {
                sb.append(gg).append(if (veryCasual) " " else ", ")
                if (!veryCasual) body = body.replaceFirstChar { it.lowercaseChar() }.let { b ->
                    // keep "I" and names capitalised
                    if (b.startsWith("i ") || b.startsWith("i'")) "I" + b.substring(1) else b
                }
            }
        }
        sb.append(body)
        signoff?.let { (word, name) ->
            val w = if (veryCasual) word.lowercase() else word
            sb.append("\n\n").append(w).append(if (veryCasual) "\n" else ",\n").append(name)
        }
        return sb.toString().trim()
    }
}
