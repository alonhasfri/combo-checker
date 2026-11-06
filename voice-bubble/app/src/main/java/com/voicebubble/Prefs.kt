package com.voicebubble

import android.content.Context
import java.io.File

data class Model(val id: String, val title: String, val detail: String, val file: String, val bytes: Long) {
    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
}

object Models {
    val all = listOf(
        Model("small", "Accurate (recommended)", "Whisper small · 190 MB · a few seconds per sentence",
            "ggml-small-q5_1.bin", 190_085_487L),
        Model("base", "Fast", "Whisper base · 60 MB · quicker, more mistakes",
            "ggml-base-q5_1.bin", 59_707_625L),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]
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

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("voice", Context.MODE_PRIVATE)
    var modelId: String
        get() = p.getString("model", "small")!!
        set(v) = p.edit().putString("model", v).apply()
    var lang: String
        get() = p.getString("lang", "auto")!!
        set(v) = p.edit().putString("lang", v).apply()
    /** Show the bubble only while a keyboard is open (it always stays while recording). */
    var onlyWithKeyboard: Boolean
        get() = p.getBoolean("onlyKb", true)
        set(v) = p.edit().putBoolean("onlyKb", v).apply()
    var bubbleX: Int
        get() = p.getInt("bx", -1)
        set(v) = p.edit().putInt("bx", v).apply()
    var bubbleY: Int
        get() = p.getInt("by", -1)
        set(v) = p.edit().putInt("by", v).apply()
}
