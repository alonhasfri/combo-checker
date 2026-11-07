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
    class Pack(val id: String, val title: String, val sub: String, val icon: Int, val tone: String,
               val names: List<String>, val hints: List<String>) {
        val size get() = names.size + hints.size
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
    )

    val all = listOf(jjk, toby, genz)

    private fun sp(c: Context) = c.getSharedPreferences("words", Context.MODE_PRIVATE)
    fun isOn(c: Context, p: Pack) = sp(c).getBoolean("pack_${p.id}", false)
    fun setOn(c: Context, p: Pack, on: Boolean) = sp(c).edit().putBoolean("pack_${p.id}", on).apply()

    fun enabled(c: Context) = all.filter { isOn(c, it) }
    fun names(c: Context) = enabled(c).flatMap { it.names }
    fun hints(c: Context) = enabled(c).flatMap { it.hints }
}
