package com.voicebubble

import android.content.Context
import java.io.File

data class Model(
    val id: String, val title: String, val detail: String, val file: String, val bytes: Long,
    /** English-only models are more accurate in English and never mis-guess the language. */
    val englishOnly: Boolean = false,
    /** Only offered on phones with the i8mm maths instructions, where this file format is fastest. */
    val fastOnly: Boolean = false,
    /** Where to download it, when it isn't in the main whisper.cpp model collection. */
    val source: String? = null,
) {
    val url get() = source ?: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
}

object Models {
    val all = listOf(
        Model("small.en-fast", "Fastest accurate · English (for your phone)",
            "Whisper small, English only · 264 MB · uses your phone's fast maths instructions",
            "ggml-small.en-q8_0.bin", 264_477_561L, englishOnly = true, fastOnly = true),
        Model("turbo.en", "Turbo · English",
            "distil-small.en · 336 MB · writes 3-5× faster on long messages, nearly as accurate",
            "ggml-distil-small.en.bin", 336_191_657L, englishOnly = true,
            source = "https://huggingface.co/distil-whisper/distil-small.en/resolve/main/ggml-distil-small.en.bin"),
        Model("small.en", "Accurate · English (recommended)", "Whisper small, English only · 190 MB · best results",
            "ggml-small.en-q5_1.bin", 190_098_681L, englishOnly = true),
        Model("base.en", "Fast · English", "Whisper base, English only · 60 MB · quickest",
            "ggml-base.en-q5_1.bin", 59_721_011L, englishOnly = true),
        Model("small", "Accurate · all languages", "Whisper small · 190 MB · Hebrew, German and more",
            "ggml-small-q5_1.bin", 190_085_487L),
        Model("base", "Fast · all languages", "Whisper base · 60 MB · quick but makes more mistakes",
            "ggml-base-q5_1.bin", 59_707_625L),
    )
    /** The models this phone can use (the fast-maths one only where it helps). */
    fun shown(): List<Model> = all.filter { !it.fastOnly || Cpu.hasI8mm() }
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: shown()[0]
    fun file(ctx: Context, m: Model) = File(File(ctx.filesDir, "models").apply { mkdirs() }, m.file)
    fun isDownloaded(ctx: Context, m: Model) = file(ctx, m).let { it.exists() && it.length() == m.bytes }
}

object Langs {
    // Whisper language codes. "auto" detects the language each time.
    val all = listOf(
        "auto" to "Auto-detect", "en" to "English", "he" to "Hebrew", "de" to "German",
        "fr" to "French", "it" to "Italian", "es" to "Spanish", "pt" to "Portuguese",
        "ar" to "Arabic", "ru" to "Russian", "nl" to "Dutch", "tr" to "Turkish",
    )
}

class Prefs(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("voice", Context.MODE_PRIVATE)
    /** Positions are remembered separately for portrait ("") and landscape ("_l"). */
    private val orient get() =
        if (ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "_l" else ""
    /** Never picked: use a model that's already downloaded (older builds' default), else the recommended one. */
    var modelId: String
        get() = p.getString("model", null)
            ?: Models.shown().firstOrNull { Models.isDownloaded(ctx, it) }?.id ?: Models.shown()[0].id
        set(v) = p.edit().putString("model", v).apply()
    /** Slower but more careful: full 30 s window and several guesses. */
    var extraAccuracy: Boolean
        get() = p.getBoolean("extraAcc", false)
        set(v) = p.edit().putBoolean("extraAcc", v).apply()
    var lang: String
        get() = p.getString("lang", "auto")!!
        set(v) = p.edit().putString("lang", v).apply()
    /** Show the bubble only while a keyboard is open (it always stays while recording). */
    var onlyWithKeyboard: Boolean
        get() = p.getBoolean("onlyKb", true)
        set(v) = p.edit().putBoolean("onlyKb", v).apply()
    /** Set once a keyboard has been detected on this phone: proof that hiding the bubble is safe. */
    /**
     * Keyboard detection confirmed on this phone: the app saw its own keyboard open and the bubble's
     * detector agreed (or the accessibility service is on). Only then may the bubble hide.
     */
    var keyboardVerified: Boolean
        get() = p.getBoolean("kbVerified", false)
        set(v) = p.edit().putBoolean("kbVerified", v).apply()
    /** Detection was tested and didn't work: the bubble always shows. */
    var keyboardBroken: Boolean
        get() = p.getBoolean("kbBroken", false)
        set(v) = p.edit().putBoolean("kbBroken", v).apply()
    var keyboardSeen: Boolean
        get() = p.getBoolean("kbSeen", false)
        set(v) = p.edit().putBoolean("kbSeen", v).apply()
    /** "s", "m" or "l". */
    var bubbleSize: String
        get() = p.getString("bsize", "m")!!
        set(v) = p.edit().putString("bsize", v).apply()
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()
    /** While a keyboard is open, sit right above it (instead of where you left it). */
    var dockAboveKeyboard: Boolean
        get() = p.getBoolean("dock", true)
        set(v) = p.edit().putBoolean("dock", v).apply()
    /** How far above the keyboard you dragged it (px). */
    var dockLift: Int
        get() = p.getInt("dockLift" + orient, 0)
        set(v) = p.edit().putInt("dockLift" + orient, v).apply()
    /** Everything you dictate comes out in CAPITALS (toggled with the ABC button while speaking). */
    var capsLock: Boolean
        get() = p.getBoolean("caps", false)
        set(v) = p.edit().putBoolean("caps", v).apply()
    /** Translate everything into this language (null = off). "English" works on the phone; others need an AI key. */
    var translateTo: String?
        get() = p.getString("translateTo", null)
        set(v) = p.edit().putString("translateTo", v).apply()
    /** Bubble look: see Skins. */
    var skin: String
        get() = p.getString("skin", "classic")!!
        set(v) = p.edit().putString("skin", v).apply()
    /** "off", "chime" or "blip". */
    var sound: String
        get() = p.getString("sound", "off")!!
        set(v) = p.edit().putString("sound", v).apply()
    /** Bubble size in dp (pinch the bubble to change it). */
    var bubbleSizeDp: Int
        get() = p.getInt("bsizeDp", when (bubbleSize) { "s" -> 58; "l" -> 88; else -> 72 })
        set(v) = p.edit().putInt("bsizeDp", v.coerceIn(44, 124)).apply()
    /** What you say first to give a command (default "out of context"). */
    var commandWord: String
        get() = p.getString("cmdWord", Commands.DEFAULT_WORD)!!.ifBlank { Commands.DEFAULT_WORD }
        set(v) = p.edit().putString("cmdWord", v.trim()).apply()
    /** Show the words next to the bubble as they're worked out, while you're still talking. */
    var liveText: Boolean
        get() = p.getBoolean("liveText", true)
        set(v) = p.edit().putBoolean("liveText", v).apply()
    /** Offer to add words you correct to the dictionary (autotype version). */
    var learnFixes: Boolean
        get() = p.getBoolean("learn", true)
        set(v) = p.edit().putBoolean("learn", v).apply()
    /** Fastest thread count measured on this phone for a model file (0 = not measured yet). */
    fun threadsFor(modelFile: String) = p.getInt("threads_$modelFile", 0)
    fun setThreadsFor(modelFile: String, n: Int) = p.edit().putInt("threads_$modelFile", n).apply()
    /** Everything you dictate is rewritten to sound like this person or style (null = off). Needs an AI key. */
    var persona: String?
        get() = p.getString("persona", null)
        set(v) = p.edit().putString("persona", v?.trim()?.ifEmpty { null }).apply()
    /** With a Claude key: quicker answers from Claude Haiku 5.5 instead of Claude Opus 5.5. */
    var aiFast: Boolean
        get() = p.getBoolean("aiFast", false)
        set(v) = p.edit().putBoolean("aiFast", v).apply()
    /** Shakespeare without AI: instant, on the phone (simpler than the AI version). */
    var instantShakespeare: Boolean
        get() = p.getBoolean("instantShakespeare", false)
        set(v) = p.edit().putBoolean("instantShakespeare", v).apply()
    /** "whisper" or "live" (streaming, types as you talk; English only). */
    var engine: String
        get() = p.getString("engine", "whisper")!!
        set(v) = p.edit().putString("engine", v).apply()
    var liveModelId: String
        get() = p.getString("liveModel", "kroko")!!
        set(v) = p.edit().putString("liveModel", v).apply()
    /** Longest message in characters, everywhere (0 = no limit). Apps can have their own (Style tab). */
    var maxChars: Int
        get() = p.getInt("maxChars", 0)
        set(v) = p.edit().putInt("maxChars", v).apply()
    /** Live engine + autotype: type the words into the text box while you talk. */
    var typeWhileTalking: Boolean
        get() = p.getBoolean("typeLive", true)
        set(v) = p.edit().putBoolean("typeLive", v).apply()
    /** Live engine: type the draft live, then swap in Whisper's more accurate text at the end. */
    var liveFinalWhisper: Boolean
        get() = p.getBoolean("liveFinalWhisper", true)
        set(v) = p.edit().putBoolean("liveFinalWhisper", v).apply()
    /** What happened the last time the Live engine was started ("ok" or the problem), for the model screen. */
    var liveStatus: String
        get() = p.getString("liveStatus", "")!!
        set(v) = p.edit().putString("liveStatus", v).apply()
    var bubbleX: Int
        get() = p.getInt("bx" + orient, -1)
        set(v) = p.edit().putInt("bx" + orient, v).apply()
    var bubbleY: Int
        get() = p.getInt("by" + orient, -1)
        set(v) = p.edit().putInt("by" + orient, v).apply()
}
