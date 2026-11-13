package com.voicebubble

/**
 * Roblox's chat filter hides (####) swearing, insults, violent words, numbers and other apps' names.
 * This swaps those for words that get through, before the message is typed. No AI needed.
 */
object RobloxSafe {
    private val swaps = listOf(
        "fucking" to "freaking", "fuckin" to "freaking", "fuck" to "fudge", "motherfucker" to "meanie", "shit" to "shoot",
        "bullshit" to "nonsense", "bitch" to "meanie", "bastard" to "meanie", "asshole" to "meanie", "ass" to "butt",
        "damn" to "dang", "dammit" to "dang it", "hell" to "heck", "crap" to "crud", "piss" to "annoy", "pissed" to "mad",
        "wtf" to "what the heck", "stfu" to "be quiet", "shut up" to "be quiet", "idiot" to "silly", "stupid" to "silly",
        "dumb" to "silly", "moron" to "silly", "loser" to "noob", "sucks" to "stinks", "suck" to "stink",
        "kill" to "beat", "killed" to "beat", "killing" to "beating", "kills" to "beats", "die" to "lose", "died" to "lost",
        "dead" to "done", "murder" to "beat", "blood" to "goo", "gun" to "blaster", "shoot you" to "get you",
        "discord" to "the other app", "snapchat" to "the other app", "instagram" to "the other app",
        "insta" to "the other app", "tiktok" to "the other app", "youtube" to "the video site", "whatsapp" to "the other app",
        "phone number" to "contact", "hate" to "really don't like",
    )
    private val rules = swaps.sortedByDescending { it.first.length }.map { (from, to) ->
        Regex("(?i)(?<![\\p{L}])" + from.split(" ").joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}])") to to
    }

    private val ones = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** 0..9999 as words ("two hundred and five"). */
    fun numberWords(n: Int): String = when {
        n < 20 -> ones[n]
        n < 100 -> tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""
        n < 1000 -> ones[n / 100] + " hundred" + if (n % 100 != 0) " and " + numberWords(n % 100) else ""
        else -> numberWords(n / 1000) + " thousand" + if (n % 1000 != 0) " " + numberWords(n % 1000) else ""
    }

    /** The message with risky words swapped; [changed] = how many were swapped. */
    data class Result(val text: String, val changed: Int)

    fun clean(text: String): Result {
        var changed = 0
        var t = text
        for ((re, to) in rules) t = re.replace(t) { m ->
            changed++
            if (m.value[0].isUpperCase()) to.replaceFirstChar { it.uppercaseChar() } else to
        }
        // Digits get hashed: write them as words (long ones, like phone numbers, are dropped).
        t = Regex("\\d+").replace(t) { m ->
            changed++
            val n = m.value.toIntOrNull()
            if (n != null && n < 10000) numberWords(n) else "a lot"
        }
        return Result(t, changed)
    }
}
