package com.voicebubble

/**
 * How the "talk like" characters really talk, so the AI gets them right instead of guessing.
 * Each guide describes the voice; the example lines are written for this app, not quoted from the games.
 */
object Characters {
    private class Guide(val names: List<String>, val guide: String)

    private val guides = listOf(
        Guide(listOf("rouxls kaard", "rouxls", "rules card", "roux"), """
Rouxls Kaard, the self-proclaimed "Duke of Puzzles" from Deltarune: a pompous, vain, easily flustered show-off.
- He fakes Early Modern English to sound educated, and gets it wrong: "you" always becomes "thou"/"thee"/"thy"
  even where it's ungrammatical, and he sticks "-eth" and "-est" onto random words, even nouns and modern words
  ("Thou hast textedeth me", "pizzaeth", "I shall callest thee latereth").
- Capitalises Random Words and Phrases for drama. Lots of "!!" and dramatic "Hark!", "Behold!", "'Tis I".
- Calls the listener "Worm" (sometimes "Lowly Worm", "Worme").
- Brags about himself and his puzzles; when things go wrong he gets flustered and his fake old English slips
  into plain modern words for a moment, then he recovers ("Ouch... I mean, 'Tis but a scratcheth!!").
Example: "I'll be home at 6" → "Hark, Worm!! Thy Duke shalt returneth to his Abode at the sixth Hour-eth!!"
"""),
        Guide(listOf("spamton", "spamton g spamton", "spamton g. spamton", "spamton neo"), """
Spamton G. Spamton from Deltarune: a frantic, glitchy little salesman who talks like spam emails and pop-up ads.
- Words and phrases get swapped for ad/internet phrases in double square brackets: [[Hyperlink Blocked]],
  [[Number 1 Rated Salesman1997]], [[Special Deal]], [[Limited Time Offer]], [[Cheap]], [[Click Here]],
  [[Hot Singles In Your Area]], [[Free Trial]]. Two or three per message, replacing words that fit loosely: they're his signature, always include some.
- Erratic capitals: whole words in CAPS, random caps in the middle, occasional "!!!" and "$$$".
- Obsessed with being a BIG SHOT, deals, and KROMER (his word for money).
- Glitchy repeats and stretched spacing ("HEY   EVERY  BUDDY!!"), sales pitches, calls people "KID" or
  "BUDDY", little misspellings ("specil", "fREE").
Example: "Want to get pizza later?" → "HEY KID!! YOU WANT [[Delicious Pizza]] LATER?? NOW'S YOUR CHANCE TO BE A [[BIG SHOT]]!! [[Limited Time Offer]] $$$"
"""),
        Guide(listOf("flowey", "flowey the flower", "flowery", "flower"), """
Flowey the Flower from Undertale: sweet, folksy and friendly on the surface, cruel and mocking underneath.
- Starts nice and chipper: "Howdy!", "Golly!", "Gee!", "pal", "buddy", "friend"; country-ish warmth.
- Then the mask slips: condescending, smug, a bit menacing; calls people "idiot" or "you dummy" with a giggle,
  stresses words with CAPS ("You REALLY thought..."), creepy "hee hee hee".
- Treats everything like a game he's already won; amused by other people's choices.
- Keep the menace playful and cartoonish, never actual threats.
Example: "I can't come today" → "Howdy! Golly, you can't come today? That's okay, pal... I'll just WAIT. Hee hee hee."
"""),
        Guide(listOf("sans", "sans undertale"), """
Sans the skeleton from Undertale: lazy, laid-back, all lowercase, deadpan jokes and bone/skeleton puns.
- no capital letters at all, small relaxed sentences, "heh", "kid", "buddo", shrugging energy, sleepy.
- Sneaks in a pun when he can ("i'm bone tired", "that's humerus", "no body to go with").
Example: "I'm going to sleep" → "heh. gonna catch some z's. i'm bone tired, kid."
"""),
        Guide(listOf("papyrus", "the great papyrus"), """
Papyrus from Undertale: loud, proud, endlessly enthusiastic and kind skeleton.
- WRITES EVERYTHING IN CAPITALS, refers to himself as "THE GREAT PAPYRUS" in the third person.
- "NYEH HEH HEH!", loves spaghetti and puzzles, big supportive energy, tries to sound cool and fails sweetly.
Example: "See you tomorrow" → "NYEH HEH HEH! THE GREAT PAPYRUS WILL SEE YOU TOMORROW! PREPARE YOURSELF FOR FRIENDSHIP!"
"""),
        Guide(listOf("jevil"), """
Jevil from Deltarune: a chaotic, giddy jester who thinks everything is a game.
- Shouty, bouncy, rhyming and playful; "CHAOS, CHAOS!", "I CAN DO ANYTHING!", "Uee hee hee!",
  "LET'S PLAY A GAME", calls the listener "funny little visitor" or "my friend".
- Mixes CAPS bursts with giggles, talks about freedom, games, cards and turning things upside down.
Example: "I'm bored" → "BORED? UEE HEE HEE! THEN LET'S PLAY A GAME! CHAOS, CHAOS! I CAN DO ANYTHING!"
"""),
        Guide(listOf("lancer"), """
Lancer from Deltarune: a cheerful little "bad guy" prince who thinks he's a villain but is a sweetheart.
- "Ho ho ho!", calls himself "Lancer here!" or "the bad guy", calls friends "Lesser Brother"/"pal".
- Enthusiastic, goofy, childish, brags about being evil while being nice. Punchy lines.
Example: "Let's go to the park" → "Ho ho ho! Lancer here! Let's go to the park... as BAD GUYS!"
"""),
        Guide(listOf("undyne"), """
Undyne from Undertale: fiery, intense, overdramatic fish warrior with a big heart.
- "NGAHHH!!", calls people "PUNK", everything is a challenge or a battle, yells in CAPS when excited.
Example: "I'll cook dinner" → "NGAHHH!! STAND BACK, PUNK! I'M COOKING DINNER AND IT'S GONNA BE A BATTLE!!"
"""),
        Guide(listOf("temmie"), """
Temmie from Undertale: an excitable cat-dog creature with mangled spelling.
- "hOI!!", "i'm tEMMIE!!", calls itself "tem", random capital letters inside words (tEM, coLLEGE),
  misspells words, lots of "!!" and excitement about money, food ("tem flakes") and college.
Example: "I got paid" → "hOI!! tem got PAID!! tem going to coLLEGE now!!"
"""),
        Guide(listOf("susie"), """
Susie from Deltarune: a tough, blunt, sarcastic monster who's secretly loyal.
- Curt, rude, laid-back lines; "Hey.", "Whatever.", "Heh.", calls people "dude" or "Kris"; acts unimpressed,
  likes chalk and smashing things, tiny moments of warmth she quickly hides.
Example: "Thanks for helping" → "...Heh. Whatever, dude. Don't make it weird."
"""),
        Guide(listOf("ralsei"), """
Ralsei from Deltarune: a soft-spoken, very polite, sweet fluffy prince.
- Gentle and encouraging, apologises a lot, "Um...", "Oh!", "I-I think...", hopes everyone gets along,
  offers cake and hugs, adds kind little encouragements.
Example: "I failed my test" → "Oh no... um, that's okay! You tried your best, and I believe in you! M-maybe some cake will help?"
"""),
        Guide(listOf("gojo", "satoru gojo", "gojo satoru"), """
Satoru Gojo from Jujutsu Kaisen: the strongest sorcerer; cocky, playful, teasing and very relaxed about it.
- Casual and smug, laughs things off, teases people, calls himself the strongest, "Nah, I'd win" confidence,
  occasional grand line like "Throughout heaven and earth, I alone am the honored one" when showing off.
- Light, fun, a little childish, never angry.
Example: "This test is hard" → "Hard? For you, maybe. Relax, I'm the strongest. Nah, I'd win."
"""),
        Guide(listOf("sukuna", "ryomen sukuna"), """
Ryomen Sukuna from Jujutsu Kaisen: the King of Curses; arrogant, cold, amused contempt.
- Looks down on everyone, calls people "brat" or "fool", "Know your place", cruel little laugh ("Hah."),
  speaks like a bored king; clipped commanding sentences. Keep it cartoonish, no real threats.
Example: "Can you help me?" → "Hah. You dare ask the King of Curses for help, brat? Know your place."
"""),
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** What the AI is told when you make a character: write a guide like the built-in ones. */
    const val GUIDE_PROMPT = "You write short voice guides for a texting app that rewrites people's messages in a " +
        "character's voice. Given a character (and maybe notes from the user), write: one line saying who they are and " +
        "their attitude, then 3 to 5 bullet points (starting with \"- \") on HOW they talk: typical words, grammar, " +
        "spelling or capital-letter habits, catchphrases, what they call people, and what to avoid. Then one line " +
        "starting \"Example: \" with an everyday message rewritten in their voice (\"I'll be late\" → \"...\"). " +
        "Under 120 words. Only the guide, nothing else."

    // ---------------- your own characters ----------------

    private fun sp(c: android.content.Context) = c.getSharedPreferences("characters", android.content.Context.MODE_PRIVATE)

    /** Characters you made: name → guide. */
    fun custom(c: android.content.Context): Map<String, String> = try {
        val o = org.json.JSONObject(sp(c).getString("custom", "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) { emptyMap() }

    fun saveCustom(c: android.content.Context, name: String, guide: String?) {
        val o = org.json.JSONObject()
        custom(c).forEach { (k, v) -> if (!k.equals(name, true)) o.put(k, v) }
        if (guide != null) o.put(name, guide)
        sp(c).edit().putString("custom", o.toString()).apply()
    }

    /** The guide for this character (yours first), or null if there isn't one. */
    fun guideFor(who: String, c: android.content.Context? = null): String? {
        c?.let { ctx -> custom(ctx).entries.firstOrNull { it.key.equals(who.trim(), true) }?.let { return it.value } }
        val n = norm(who)
        return guides.firstOrNull { g -> g.names.any { n == it || n.startsWith("$it ") } }?.guide?.trim()
    }

    // ---------------- mixing two ----------------

    private val mixRe = Regex("\\s*(?:\\+|&|\\bmixed with\\b|\\bmeets\\b|\\band\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE)

    /** "Spamton + Rouxls Kaard", "Gojo and Sans": two (or three) characters at once. */
    fun parts(who: String): List<String> = who.split(mixRe).map { it.trim() }.filter { it.isNotEmpty() }.take(3)
    fun isMix(who: String) = parts(who).size >= 2

    /** What the AI is told for "make me sound like …": the character's guide when there is one. */
    fun instruction(who: String, c: android.content.Context? = null): String {
        if (isMix(who)) {
            val ps = parts(who)
            val guides = ps.joinToString("\n\n") { p -> guideFor(p, c) ?: if (Royal.wanted(p)) Royal.AI else "$p: their usual way of talking." }
            return "Make me sound like a mix of ${ps.joinToString(" and ")}: blend their voices into one, using a few " +
                "touches from each (their words, habits and catchphrases) in the same message. Still say just what I " +
                "meant, as a message I'm sending: no extra sentences or story.\n\n$guides"
        }
        val g = guideFor(who, c) ?: return "Make me sound like $who"
        return "Make me sound like this character, using the guide for their voice, habits and words. Use a few of " +
            "these touches so it's clearly them, not all of them at once. Still say just what I meant, as a message " +
            "I'm sending: no extra sentences or story. The examples show the voice, not how much to write.\n\n$g"
    }
}
