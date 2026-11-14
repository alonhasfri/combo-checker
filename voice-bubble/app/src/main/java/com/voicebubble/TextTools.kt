package com.voicebubble

/**
 * Small text helpers applied to every dictation (each can be switched off):
 * voice commands ("new line", "question mark", "scratch that"), emoji by voice ("fire emoji"),
 * filler removal ("um", "uh", repeated words) and numbers & dates ("twenty five percent" → 25%).
 */
object TextTools {

    // ---------------- voice commands ----------------

    private val punctuation = listOf(
        "new paragraph" to "\n\n", "next paragraph" to "\n\n", "new line" to "\n", "next line" to "\n",
        "question mark" to "?", "exclamation mark" to "!", "exclamation point" to "!", "full stop" to ".",
        "comma" to ",", "semicolon" to ";", "colon" to ":", "ellipsis" to "…", "dot dot dot" to "…",
        "open bracket" to "(", "close bracket" to ")", "open quote" to "\"", "close quote" to "\"",
    )

    /** Turns spoken punctuation and layout words into the real thing. */
    fun voiceCommands(text: String): String {
        var t = text
        // "scratch that" / "delete that" removes the sentence said just before it.
        t = Regex("(?i)(^|(?<=[.!?\\n]))([^.!?\\n]*?[.!?,]?)\\s*\\b(?:no,?\\s+)?(?:${Commands.SCRATCH})\\b[.!?,]*\\s*").replace(t) { "" }
        for ((said, mark) in punctuation) {
            val words = "\\b" + said.replace(" ", "\\s+") + "\\b"
            t = if (mark.startsWith("\n")) {
                // Line breaks keep the sentence's own full stop: "Hello. New line. Bye." → "Hello.\nBye."
                Regex("[ \\t]*,?[ \\t]*$words[,.!?]?[ \\t]*", RegexOption.IGNORE_CASE).replace(t, mark)
            } else {
                // Punctuation replaces whatever Whisper put around the word: "you, question mark." → "you? "
                Regex("[ \\t]*[,.]?[ \\t]*$words[,.!?]?", RegexOption.IGNORE_CASE).replace(t, "$mark ")
            }
        }
        // "period" only when it ends the dictation (it's also an ordinary word).
        t = Regex("(?i)\\s*\\bperiod[.!]?\\s*$").replace(t, ".")
        // Tidy spaces, and start a capital after a line break or a new sentence.
        t = t.replace(Regex("[ \\t]+([,.!?;:…)])"), "$1")
            .replace(Regex("\\(\\s+"), "(")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .replace(Regex("([.!?][ \\t]+|\\n)(\\p{Ll})")) { it.groupValues[1] + it.groupValues[2].uppercase() }
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("([.!?])(?=\\p{Lu})"), "$1 ")   // "five.Meet" after a removed sentence
        return t.trim(' ', '\t')
    }

    // ---------------- emoji ----------------

    private val emoji = mapOf(
        "heart" to "❤️", "red heart" to "❤️", "broken heart" to "💔", "skull" to "💀", "crying laughing" to "😂",
        "laughing crying" to "😂", "laughing" to "😂", "crying" to "😭", "sob" to "😭", "fire" to "🔥",
        "thumbs up" to "👍", "thumbs down" to "👎", "smile" to "😊", "smiley" to "😊", "smiley face" to "😊",
        "happy" to "😊", "wink" to "😉", "sad" to "😢", "angry" to "😠", "shocked" to "😮", "thinking" to "🤔",
        "eyes" to "👀", "clap" to "👏", "clapping" to "👏", "pray" to "🙏", "praying" to "🙏", "party" to "🎉",
        "hundred" to "💯", "100" to "💯", "sparkles" to "✨", "sunglasses" to "😎", "cool" to "😎",
        "heart eyes" to "😍", "kiss" to "😘", "eye roll" to "🙄", "rolling eyes" to "🙄", "shrug" to "🤷",
        "facepalm" to "🤦", "ok" to "👌", "okay" to "👌", "muscle" to "💪", "flex" to "💪", "star" to "⭐",
        "check" to "✅", "check mark" to "✅", "cross" to "❌", "rocket" to "🚀", "ghost" to "👻", "clown" to "🤡",
        "moai" to "🗿", "nerd" to "🤓", "pleading" to "🥺", "puppy eyes" to "🥺", "melting" to "🫠",
        "salute" to "🫡", "sweat" to "😅", "grin" to "😁", "devil" to "😈", "poop" to "💩", "money" to "💰",
        "sleeping" to "😴", "sick" to "🤢", "mind blown" to "🤯", "hug" to "🤗", "cake" to "🎂", "gift" to "🎁",
        "sun" to "☀️", "moon" to "🌙", "rainbow" to "🌈", "dog" to "🐶", "cat" to "🐱", "goat" to "🐐",
        // Flowers and hearts
        "rose" to "🌹", "wilted rose" to "🥀", "wilting rose" to "🥀", "dead rose" to "🥀", "dying rose" to "🥀",
        "wilted flower" to "🥀", "sad rose" to "🥀", "flower" to "🌸", "cherry blossom" to "🌸", "sunflower" to "🌻",
        "tulip" to "🌷", "bouquet" to "💐", "orange heart" to "🧡", "yellow heart" to "💛", "green heart" to "💚",
        "blue heart" to "💙", "purple heart" to "💜", "black heart" to "🖤", "white heart" to "🤍", "pink heart" to "🩷",
        "sparkling heart" to "💖", "two hearts" to "💕", "heart on fire" to "❤️‍🔥", "mending heart" to "❤️‍🩹",
        // Hands
        "peace" to "✌️", "peace sign" to "✌️", "victory" to "✌️", "victory hand" to "✌️", "wave" to "👋",
        "waving" to "👋", "raised hand" to "✋", "high five" to "🙌", "raised hands" to "🙌", "fist bump" to "👊",
        "fist" to "✊", "crossed fingers" to "🤞", "fingers crossed" to "🤞", "rock on" to "🤘", "call me" to "🤙",
        "pointing up" to "☝️", "point up" to "👆", "point down" to "👇", "point left" to "👈", "point right" to "👉",
        "middle finger" to "🖕", "handshake" to "🤝", "writing hand" to "✍️", "pinch" to "🤏", "heart hands" to "🫶",
        "open hands" to "👐", "folded hands" to "🙏", "nail polish" to "💅", "selfie" to "🤳",
        // Faces
        "smiling face" to "☺️", "big smile" to "😃", "grinning" to "😀", "joy" to "😂", "rofl" to "🤣",
        "rolling on the floor" to "🤣", "upside down" to "🙃", "upside down face" to "🙃", "blush" to "😊",
        "blushing" to "😊", "halo" to "😇", "angel" to "😇", "in love" to "🥰", "smiling hearts" to "🥰",
        "star struck" to "🤩", "starstruck" to "🤩", "kissing" to "😗", "yum" to "😋", "tongue" to "😛",
        "tongue out" to "😛", "crazy" to "🤪", "zany" to "🤪", "money face" to "🤑", "shush" to "🤫",
        "shushing" to "🤫", "zipper mouth" to "🤐", "raised eyebrow" to "🤨", "neutral" to "😐",
        "expressionless" to "😑", "no mouth" to "😶", "smirk" to "😏", "unamused" to "😒", "grimace" to "😬",
        "grimacing" to "😬", "lying" to "🤥", "relieved" to "😌", "pensive" to "😔", "sleepy" to "😪",
        "drooling" to "🤤", "mask" to "😷", "hot" to "🥵", "cold" to "🥶", "freezing" to "🥶", "dizzy" to "😵",
        "exploding head" to "🤯", "cowboy" to "🤠", "partying" to "🥳", "party face" to "🥳", "disguise" to "🥸",
        "confused" to "😕", "worried" to "😟", "frowning" to "☹️", "surprised" to "😯", "astonished" to "😲",
        "flushed" to "😳", "anxious" to "😰", "scared" to "😨", "fearful" to "😨", "screaming" to "😱",
        "scream" to "😱", "disappointed" to "😞", "tired" to "😫", "weary" to "😩", "yawn" to "🥱",
        "yawning" to "🥱", "triumph" to "😤", "huffing" to "😤", "rage" to "😡", "pouting" to "😡",
        "cursing" to "🤬", "swearing" to "🤬", "skull and crossbones" to "☠️", "alien" to "👽", "robot" to "🤖",
        "jack o lantern" to "🎃", "pumpkin" to "🎃", "smiling cat" to "😸", "holding back tears" to "🥹",
        "face holding back tears" to "🥹", "saluting" to "🫡", "dotted face" to "🫥", "shaking face" to "🫨",
        "peeking" to "🫣", "hand over mouth" to "🤭", "giggle" to "🤭", "yikes" to "😬", "side eye" to "👀",
        "dead" to "💀", "crying face" to "😢", "loudly crying" to "😭", "sweat smile" to "😅", "nervous" to "😅",
        // Things and symbols
        "crown" to "👑", "gem" to "💎", "diamond" to "💎", "bomb" to "💣", "collision" to "💥", "boom" to "💥",
        "lightning" to "⚡", "zap" to "⚡", "snowflake" to "❄️", "droplet" to "💧", "water" to "💧", "ice" to "🧊",
        "trophy" to "🏆", "medal" to "🏅", "gold medal" to "🥇", "target" to "🎯", "bullseye" to "🎯",
        "game" to "🎮", "video game" to "🎮", "controller" to "🎮", "music" to "🎵", "headphones" to "🎧",
        "microphone" to "🎤", "camera" to "📷", "phone" to "📱", "laptop" to "💻", "book" to "📖", "books" to "📚",
        "pencil" to "✏️", "memo" to "📝", "pin" to "📌", "lock" to "🔒", "key" to "🔑", "bell" to "🔔",
        "light bulb" to "💡", "idea" to "💡", "warning" to "⚠️", "stop sign" to "🛑", "no entry" to "⛔",
        "question" to "❓", "exclamation" to "❗", "plus" to "➕", "infinity" to "♾️", "recycle" to "♻️",
        "sparkle" to "✨", "glowing star" to "🌟", "shooting star" to "🌠", "dizzy star" to "💫", "comet" to "☄️",
        "speech bubble" to "💬", "thought bubble" to "💭", "zzz" to "💤", "sleep" to "💤", "balloon" to "🎈",
        "confetti" to "🎊", "fireworks" to "🎆", "christmas tree" to "🎄", "candle" to "🕯️", "hourglass" to "⏳",
        "clock" to "⏰", "alarm" to "⏰", "calendar" to "📅", "chart" to "📈", "chart up" to "📈", "chart down" to "📉",
        "dollar" to "💵", "credit card" to "💳", "shopping" to "🛍️", "house" to "🏠", "car" to "🚗",
        "plane" to "✈️", "airplane" to "✈️", "earth" to "🌍", "globe" to "🌍", "mountain" to "⛰️", "beach" to "🏖️",
        "volcano" to "🌋", "cloud" to "☁️", "rain" to "🌧️", "umbrella" to "☂️", "snowman" to "⛄",
        "heavy check" to "✔️", "red x" to "❌", "red circle" to "🔴", "green circle" to "🟢", "flag" to "🚩",
        "red flag" to "🚩", "white flag" to "🏳️", "pirate flag" to "🏴‍☠️", "eye" to "👁️", "brain" to "🧠",
        "lips" to "👄", "biting lip" to "🫦", "tooth" to "🦷", "bone" to "🦴", "footprints" to "👣",
        // Food
        "pizza" to "🍕", "burger" to "🍔", "hamburger" to "🍔", "fries" to "🍟", "hot dog" to "🌭", "taco" to "🌮",
        "sushi" to "🍣", "ramen" to "🍜", "noodles" to "🍜", "rice" to "🍚", "bread" to "🍞", "egg" to "🥚",
        "cookie" to "🍪", "donut" to "🍩", "ice cream" to "🍦", "chocolate" to "🍫", "candy" to "🍬",
        "popcorn" to "🍿", "coffee" to "☕", "tea" to "🍵", "beer" to "🍺", "cheers" to "🥂", "wine" to "🍷",
        "apple" to "🍎", "banana" to "🍌", "strawberry" to "🍓", "cherry" to "🍒", "peach" to "🍑",
        "eggplant" to "🍆", "avocado" to "🥑", "lemon" to "🍋", "watermelon" to "🍉", "grapes" to "🍇",
        "salt" to "🧂", "cheese" to "🧀", "milk" to "🥛",
        // Animals
        "fox" to "🦊", "bear" to "🐻", "panda" to "🐼", "frog" to "🐸", "monkey" to "🐒", "see no evil" to "🙈",
        "hear no evil" to "🙉", "speak no evil" to "🙊", "lion" to "🦁", "tiger" to "🐯", "horse" to "🐴",
        "unicorn" to "🦄", "pig" to "🐷", "cow" to "🐮", "chicken" to "🐔", "penguin" to "🐧", "bird" to "🐦",
        "eagle" to "🦅", "owl" to "🦉", "duck" to "🦆", "snake" to "🐍", "turtle" to "🐢", "dragon" to "🐉",
        "dinosaur" to "🦖", "t rex" to "🦖", "whale" to "🐳", "dolphin" to "🐬", "fish" to "🐟", "shark" to "🦈",
        "octopus" to "🐙", "butterfly" to "🦋", "bee" to "🐝", "bug" to "🐛", "spider" to "🕷️", "snail" to "🐌",
        "rabbit" to "🐰", "bunny" to "🐰", "mouse" to "🐭", "hamster" to "🐹", "wolf" to "🐺", "sloth" to "🦥",
        "crab" to "🦀", "shrimp" to "🦐", "rat" to "🐀", "worm" to "🪱",
        // Sports
        "soccer" to "⚽", "football" to "⚽", "basketball" to "🏀", "tennis" to "🎾", "running" to "🏃",
        "weightlifting" to "🏋️", "swimming" to "🏊", "bicycle" to "🚲", "boxing" to "🥊", "dice" to "🎲",
    )
    // Up to four words before "emoji" ("face holding back tears emoji"); "t-rex" and "jack-o-lantern" too.
    private val emojiRe = Regex("\\b(?:([\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,3})|(100))\\s+emojis?\\b", RegexOption.IGNORE_CASE)

    /** "fire emoji" → 🔥. The longest name that matches wins; words before it stay ("big fire emoji" → "big 🔥"). */
    fun emoji(text: String): String = emojiRe.replace(text) { m ->
        val original = m.groupValues[1].ifEmpty { m.groupValues[2] }.split(Regex("\\s+"))
        val words = original.map { it.lowercase().replace('-', ' ') }
        for (start in words.indices) {
            val name = words.subList(start, words.size).joinToString(" ")
            val e = emoji[name] ?: continue
            // Keep the original spelling of the words that come before the emoji name.
            val keep = original.take(start).joinToString(" ")
            return@replace if (keep.isEmpty()) e else "$keep $e"
        }
        m.value
    }.replace(Regex("\\s+([,.!?])(?=\\s|$)"), "$1")

    // ---------------- filler words ----------------

    private val keepDoubled = setOf("bye", "no", "ha", "haha", "la", "so", "very", "really", "yeah", "yes", "go",
        "now", "well", "knock", "chop", "there", "night", "aye", "boo", "choo", "tsk", "hey", "wait", "okay", "please")

    private val fillerRe = Regex("(?i)(^|[\\s,])(?:um+|uh+|uhm+|erm+|er|ah+|hmm+|mm+)(?=[\\s,.!?]|$)[,]?")

    /** Removes "um", "uh"… and stutters like "I I think" or "the the". */
    fun removeFillers(text: String): String {
        var t = fillerRe.replace(text) { it.groupValues[1].let { pre -> if (pre == ",") "," else pre } }
        t = Regex("(?i)\\b(\\p{L}+)(\\s+\\1\\b)+").replace(t) { m ->
            // Some words are doubled on purpose ("bye bye", "no no no", "very very").
            if (m.groupValues[1].lowercase() in keepDoubled) m.value else m.groupValues[1]
        }
        t = t.replace(Regex("\\s+,"), ",").replace(Regex(",\\s*([.!?])"), "$1").replace(Regex("^[,\\s]+"), "")
            .replace(Regex("[ \\t]{2,}"), " ").trim()
        return t.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }.let { out ->
            // Keep the original's first letter case if it had no capital to begin with.
            if (text.firstOrNull()?.isLowerCase() == true) out.replaceFirstChar { it.lowercaseChar() } else out
        }
    }

    // ---------------- numbers & dates ----------------

    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90,
    )
    private val ordinals = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13,
        "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
        "nineteenth" to 19, "twentieth" to 20, "thirtieth" to 30,
    )
    private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December")

    private val numWord = (units.keys + tens.keys + listOf("hundred", "thousand", "million")).joinToString("|")
    private val numRe = Regex("(?i)\\b(?:$numWord)(?:[\\s-]+(?:and[\\s-]+)?(?:$numWord))*\\b")

    /** "two hundred and fifty" → 250. Null when it isn't a number. */
    fun parseNumber(words: String): Long? {
        var total = 0L
        var current = 0L
        var any = false
        for (w in words.lowercase().split(Regex("[\\s-]+")).filter { it.isNotEmpty() && it != "and" }) {
            when {
                units[w] != null -> { current += units.getValue(w); any = true }
                tens[w] != null -> { current += tens.getValue(w); any = true }
                w == "hundred" -> { current = (if (current == 0L) 1 else current) * 100; any = true }
                w == "thousand" -> { total += (if (current == 0L) 1 else current) * 1000; current = 0; any = true }
                w == "million" -> { total += (if (current == 0L) 1 else current) * 1_000_000; current = 0; any = true }
                else -> return null
            }
        }
        return if (any) total + current else null
    }

    /**
     * Number words become digits when it's a real quantity (10 or more, or with a unit like
     * percent / dollars / pm). Small counts stay words: "two cats" stays, "twenty five cats" → 25 cats.
     */
    fun numbersAndDates(text: String): String {
        val monthAlt = months.joinToString("|")
        // Dates: "March third" / "the third of March" → "March 3".
        var t = Regex("(?i)\\b($monthAlt)\\s+(?:the\\s+)?(" + (ordinals.keys + listOf("twenty first", "twenty second",
            "twenty third", "twenty fourth", "twenty fifth", "twenty sixth", "twenty seventh", "twenty eighth",
            "twenty ninth", "thirty first")).sortedByDescending { it.length }.joinToString("|") { it.replace(" ", "[\\s-]") } + ")\\b")
            .replace(text) { m -> "${cap(m.groupValues[1])} ${ordinalValue(m.groupValues[2])}" }
        t = Regex("(?i)\\bthe\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+of\\s+($monthAlt)\\b").replace(t) { m ->
            "${cap(m.groupValues[2])} ${m.groupValues[1]}"
        }
        // Spelled-out numbers.
        t = numRe.replace(t) { m ->
            val n = parseNumber(m.value) ?: return@replace m.value
            val after = t.substring(m.range.last + 1).trimStart().lowercase()
            val unit = Regex("^(percent|per cent|dollars?|euros?|pounds?|shekels?|pm|am|p\\.m\\.|a\\.m\\.|o'clock|minutes?|hours?|seconds?|days?|weeks?|months?|years?|km|kilometers?|miles?|kg|kilos?|%)\\b").containsMatchIn(after)
            if (n >= 10 || unit) n.toString() else m.value
        }
        // Units and money.
        t = Regex("(?i)(\\d+)\\s*(?:percent|per cent)\\b").replace(t, "$1%")
        t = Regex("(?i)(\\d+)\\s*dollars?\\b").replace(t, "\\$$1")
        t = Regex("(?i)(\\d+)\\s*euros?\\b").replace(t, "€$1")
        t = Regex("(?i)(\\d+)\\s*pounds\\b").replace(t, "£$1")
        t = Regex("(?i)(\\d+)\\s*o'clock\\b").replace(t, "$1:00")
        t = Regex("(?i)\\b(\\d{1,2})\\s*(?:p\\.\\s?m\\.?|pm)(?=\\W|$)").replace(t, "$1 pm")
        t = Regex("(?i)\\b(\\d{1,2})\\s*(?:a\\.\\s?m\\.?|am)(?=\\W|$)").replace(t, "$1 am")
        return t
    }

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercaseChar() }

    private fun ordinalValue(s: String): Int {
        val w = s.lowercase().split(Regex("[\\s-]+"))
        return if (w.size == 2) (tens[w[0]] ?: 0) + (ordinals[w[1]] ?: 0) else ordinals[w[0]] ?: 0
    }
}
