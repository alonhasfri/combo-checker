package com.voicebubble

import android.content.Context

/**
 * Ready-made word packs: switch one on and its words count as part of your dictionary.
 *
 * - names: proper nouns. Given to Whisper and corrected when it writes something that sounds
 *   the same (strictly, so normal words aren't changed).
 * - hints: ordinary words and phrases (slang, terms). Only given to Whisper as a hint, never
 *   used to change other words, so "mowing" never turns into "mewing".
 */
object Packs {
    /**
     * [fixes]: how speech engines often mishear a word ("the lulu" → "delulu"). Swapped exactly,
     * only while the pack is on.
     */
    class Pack(val id: String, val title: String, val sub: String, val icon: Int, val tone: String,
               val names: List<String>, val hints: List<String>, val fixes: Map<String, String> = emptyMap()) {
        val size get() = names.size + hints.size
        val fixRules by lazy {
            fixes.entries.sortedByDescending { it.key.length }.map { (from, to) ->
                Regex("(?<![\\p{L}'’])" + from.split(" ").joinToString("[\\s-]+") { Regex.escape(it) } + "(?![\\p{L}'’])",
                    RegexOption.IGNORE_CASE) to to
            }
        }
    }

    /** Applies the switched-on packs' known mishearings. */
    fun fix(packs: List<Pack>, text: String): String {
        var t = text
        for (p in packs) for ((re, to) in p.fixRules) t = re.replace(t) { m ->
            if (m.value[0].isUpperCase()) to.replaceFirstChar { it.uppercaseChar() } else to
        }
        return t
    }

    val jjk = Pack(
        "jjk", "Jujutsu Kaisen", "Characters, techniques and places", R.drawable.ui_zap, "purple",
        names = listOf(
            "Jujutsu Kaisen", "Yuji Itadori", "Itadori", "Megumi Fushiguro", "Fushiguro", "Nobara Kugisaki", "Kugisaki",
            "Satoru Gojo", "Gojo", "Ryomen Sukuna", "Sukuna", "Suguru Geto", "Geto", "Kento Nanami", "Nanami",
            "Maki Zenin", "Mai Zenin", "Zenin", "Toge Inumaki", "Inumaki", "Yuta Okkotsu", "Okkotsu", "Rika Orimoto",
            "Kenjaku", "Mahito", "Jogo", "Hanami", "Dagon", "Choso", "Aoi Todo", "Todo", "Noritoshi Kamo",
            "Kasumi Miwa", "Mechamaru", "Momo Nishimiya", "Masamichi Yaga", "Shoko Ieiri", "Ijichi", "Toji Fushiguro",
            "Naoya Zenin", "Naobito Zenin", "Hiromi Higuruma", "Higuruma", "Hajime Kashimo", "Kashimo",
            "Kinji Hakari", "Hakari", "Kirara Hoshi", "Takaba", "Hana Kurusu", "Yorozu", "Ryu Ishigori", "Takako Uro",
            "Uraume", "Yuki Tsukumo", "Mei Mei", "Riko Amanai", "Tengen", "Utahime Iori", "Gakuganji", "Kusakabe",
            "Mahoraga", "Domain Expansion", "Infinite Void", "Malevolent Shrine", "Hollow Purple", "Black Flash",
            "Reverse Cursed Technique", "Six Eyes", "Ten Shadows", "Chimera Shadow Garden", "Shibuya Incident",
            "Culling Game", "Jujutsu High", "Heian", "Fuga", "Gege Akutami",
        ),
        hints = listOf(
            "cursed energy", "cursed technique", "cursed spirit", "curse user", "jujutsu sorcerer", "Limitless",
            "Simple Domain", "Binding Vow", "Heavenly Restriction", "Boogie Woogie", "Cleave", "Dismantle",
            "Divine Dogs", "Nue", "Idle Transfiguration", "Nah I'd win", "Stand proud", "throughout heaven and earth",
        ),
        fixes = mapOf(
            "go joe" to "Gojo", "go jo" to "Gojo", "sakuna" to "Sukuna", "sukona" to "Sukuna",
            "sue kuna" to "Sukuna", "su kuna" to "Sukuna", "sukoona" to "Sukuna", "ita dori" to "Itadori",
            "itta dori" to "Itadori", "megumi" to "Megumi", "nobara" to "Nobara", "nana me" to "Nanami",
            "mahoraga" to "Mahoraga", "maho raga" to "Mahoraga", "ma hito" to "Mahito", "kenjaku" to "Kenjaku",
            "ken jaku" to "Kenjaku", "hakari" to "Hakari", "hack ari" to "Hakari",
            "jiu jitsu kaisen" to "Jujutsu Kaisen", "jujitsu kaisen" to "Jujutsu Kaisen", "ju jitsu kaisen" to "Jujutsu Kaisen",
            "ryomen" to "Ryomen", "geto" to "Geto", "getto" to "Geto", "okotsu" to "Okkotsu",
            "domain expansions" to "Domain Expansions", "black flash" to "Black Flash", "toji" to "Toji", "toe ji" to "Toji",
        ),
    )

    val toby = Pack(
        "toby", "Deltarune & Undertale", "Characters, places and songs", R.drawable.ui_star, "gold",
        names = listOf(
            "Undertale", "Deltarune", "Toby Fox", "Frisk", "Chara", "Flowey", "Toriel", "Asgore", "Asriel", "Dreemurr",
            "Sans", "Papyrus", "Undyne", "Alphys", "Mettaton", "Napstablook", "Muffet", "Grillby", "Gaster",
            "W. D. Gaster", "Temmie", "Monster Kid", "Mad Dummy", "Gerson", "Burgerpants", "Snowdin", "Hotland",
            "New Home", "True Lab", "Megalovania", "Bonetrousle", "Spear of Justice", "Hopes and Dreams",
            "Kris", "Susie", "Ralsei", "Noelle", "Berdly", "Lancer", "Rouxls Kaard", "Jevil", "Spamton",
            "Spamton NEO", "Tenna", "Roaring Knight", "Rudinn", "Hathy", "K. Round", "Tasque Manager", "Swatchling",
            "Werewire", "Mauswheel", "Sweet Cap'n Cakes", "Card Castle", "Cyber City", "Castle Town", "Hometown",
            "Darkner", "Lightner", "Snowgrave", "Kromer", "BIG SHOT", "Pipis", "Starwalker", "Catti", "Jockington",
            "Alvin", "Dark World", "Light World", "Field of Hopes and Dreams", "Scarlet Forest", "Queen's Mansion",
        ),
        hints = listOf(
            "Determination", "Pacifist", "Genocide", "the Underground", "the Ruins", "Waterfall", "the Fountain",
            "King", "Queen", "Seam", "Clover", "Titan", "Mike", "Weird Route", "Freezing Ring", "Thorn Ring",
            "Hammer of Justice", "Knight",
        ),
        fixes = mapOf(
            "delta rune" to "Deltarune", "deltarun" to "Deltarune", "delta run" to "Deltarune", "under tale" to "Undertale",
            "undertail" to "Undertale", "under tail" to "Undertale", "rules card" to "Rouxls Kaard", "rule's card" to "Rouxls Kaard",
            "rools card" to "Rouxls Kaard", "roux card" to "Rouxls Kaard", "rouxls card" to "Rouxls Kaard",
            "spam ton" to "Spamton", "spamtin" to "Spamton", "ralsay" to "Ralsei", "ralsy" to "Ralsei", "ralsie" to "Ralsei",
            "ral say" to "Ralsei", "undine" to "Undyne", "alfis" to "Alphys", "alphis" to "Alphys",
            "flowy" to "Flowey", "flowie" to "Flowey", "tori el" to "Toriel", "tory el" to "Toriel", "toriel" to "Toriel",
            "as gore" to "Asgore", "azgore" to "Asgore", "azriel" to "Asriel", "mega lovania" to "Megalovania",
            "megalovania" to "Megalovania", "megalo vania" to "Megalovania", "meta ton" to "Mettaton", "metaton" to "Mettaton",
            "metta ton" to "Mettaton", "jeville" to "Jevil", "jay vil" to "Jevil", "jevel" to "Jevil", "napsta blook" to "Napstablook",
            "temmy" to "Temmie", "berdley" to "Berdly", "bird lee" to "Berdly", "susie" to "Susie",
            "pippis" to "Pipis", "snow grave" to "Snowgrave",
        ),
    )

    val genz = Pack(
        "genz", "Gen Z slang", "So it spells slang the way you mean it", R.drawable.ui_spark, "orange",
        names = listOf("Skibidi", "Fanum tax", "Ohio", "GOAT", "IJBOL"),
        hints = listOf(
            "rizz", "rizzler", "no cap", "cap", "bussin", "slay", "delulu", "sus", "gyatt", "sigma", "aura", "mewing",
            "glazing", "yapping", "cooked", "based", "ratio", "mid", "bet", "fr", "ngl", "tbh", "lowkey", "highkey",
            "it's giving", "ate", "periodt", "situationship", "NPC", "main character", "era", "vibe check",
            "understood the assignment", "touch grass", "goated", "ick", "bruh", "sheesh", "oomf", "iykyk",
            "stan", "simp", "red flag", "green flag", "beige flag", "brainrot", "aura points", "mogging",
            "looksmaxxing", "crash out", "opp", "pookie", "demure", "unc", "chopped", "tweaking", "huzz", "six seven",
        ),
        fixes = mapOf(
            "riz" to "rizz", "ris" to "rizz", "rizler" to "rizzler", "rizzlers" to "rizzlers", "risler" to "rizzler",
            "the lulu" to "delulu", "de lulu" to "delulu", "delu lu" to "delulu", "dalulu" to "delulu", "delulo" to "delulu",
            "gyat" to "gyatt", "gyad" to "gyatt", "guiat" to "gyatt", "skibbidy" to "skibidi", "skibbidi" to "skibidi",
            "skibidy" to "skibidi", "skibbity" to "skibidi", "skibity" to "skibidi", "skibidee" to "skibidi",
            "phantom tax" to "Fanum tax", "fanem tax" to "Fanum tax", "fan um tax" to "Fanum tax", "fanum tax" to "Fanum tax",
            "bussing" to "bussin", "busin" to "bussin", "no capp" to "no cap", "unk" to "unc",
            "huz" to "huzz", "i j b o l" to "IJBOL", "ijbol" to "IJBOL", "periodt" to "periodt", "period t" to "periodt",
            "sigma's" to "sigmas", "mewing" to "mewing", "looks maxing" to "looksmaxxing", "looksmaxing" to "looksmaxxing",
            "looks maxxing" to "looksmaxxing", "goated" to "goated", "goaded" to "goated", "brain rot" to "brainrot",
            "situation ship" to "situationship", "pookey" to "pookie", "pooky" to "pookie", "glazin" to "glazing",
            "yappin" to "yapping", "crashing out" to "crashing out", "aura points" to "aura points",
        ),
    )

    val all = listOf(jjk, toby, genz)

    private fun sp(c: Context) = c.getSharedPreferences("words", Context.MODE_PRIVATE)
    fun isOn(c: Context, p: Pack) = sp(c).getBoolean("pack_${p.id}", false)
    fun setOn(c: Context, p: Pack, on: Boolean) = sp(c).edit().putBoolean("pack_${p.id}", on).apply()

    fun enabled(c: Context) = all.filter { isOn(c, it) }
    fun names(c: Context) = enabled(c).flatMap { it.names }
    fun hints(c: Context) = enabled(c).flatMap { it.hints }
}
