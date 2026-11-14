package com.voicebubble

/**
 * How much of Whisper's 30-second window to process: 50 steps per second spoken, plus a margin,
 * never less than 512 (shorter windows start to hurt accuracy). 1500 = the full window.
 */
fun audioCtxFor(samples: Int): Int {
    val seconds = samples / 16000.0
    return (Math.ceil(seconds * 50).toInt() + 128).coerceIn(512, 1500)
}

/**
 * Whisper sometimes goes wrong on a piece of audio and writes numbers, symbols or the same word over and
 * over. True when the text looks like that (so it's done again, or the Live engine's text is used).
 */
fun looksBroken(text: String): Boolean {
    val chars = text.filter { !it.isWhitespace() }
    if (chars.length < 3) return false
    val letters = chars.count { it.isLetter() }
    if (letters < chars.length * 0.45) return true
    val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }
    return words.size >= 8 && words.distinct().size < words.size * 0.3
}

/** Letters in a text: a rough measure of how many words it caught. */
fun letterCount(text: String) = text.count { it.isLetter() }

/** On-device Whisper speech recognition (whisper.cpp). Keeps one model loaded in memory. */
object Whisper {
    /** True when this phone runs the faster engine build (has the i8mm maths instructions). */
    val fastCpu: Boolean = Cpu.hasI8mm()

    init { System.loadLibrary(if (fastCpu) "voicebubble_fast" else "voicebubble_base") }

    /** Processor threads to use (measured once per phone; see [benchmark]). */
    @Volatile var threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    private external fun nativeLoad(path: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeAbort()
    private external fun nativeTranscribe(
        ptr: Long, audio: FloatArray, lang: String, prompt: String, threads: Int, audioCtx: Int, beams: Int, translate: Boolean,
    ): ByteArray?


    private val lock = Any()
    private var ctx = 0L
    private var loadedPath: String? = null

    /** Loads the model if it isn't loaded yet. Returns false when the file can't be read. */
    fun ensureLoaded(path: String): Boolean = synchronized(lock) {
        if (ctx != 0L && loadedPath == path) return true
        if (ctx != 0L) { nativeFree(ctx); ctx = 0L; loadedPath = null }
        val p = nativeLoad(path)
        if (p == 0L) return false
        ctx = p
        loadedPath = path
        true
    }

    fun release() = synchronized(lock) {
        if (ctx != 0L) nativeFree(ctx)
        ctx = 0L
        loadedPath = null
    }

    /**
     * Tries 2, 4, 6 and 8 threads on a few seconds of test sound and returns the fastest.
     * Phones differ a lot (how many fast cores, how they share heat), so measuring beats guessing.
     */
    fun benchmark(modelPath: String): Int = synchronized(lock) {
        if (!ensureLoaded(modelPath)) return threads
        val cores = Runtime.getRuntime().availableProcessors()
        val audio = FloatArray(16000 * 4) { i -> (0.2 * Math.sin(2 * Math.PI * 180 * i / 16000.0) * Math.sin(Math.PI * i / 8000.0)).toFloat() }
        var best = threads
        var bestMs = Long.MAX_VALUE
        for (t in listOf(2, 4, 6, 8).filter { it <= cores }) {
            val start = System.nanoTime()
            nativeTranscribe(ctx, audio, "en", "", t, 512, 1, false)
            val ms = (System.nanoTime() - start) / 1_000_000
            if (ms < bestMs * 0.95) { bestMs = ms; best = t }   // only switch for a real gain
        }
        threads = best
        best
    }

    /** Stops a transcription that is running on another thread. */
    fun abort() = nativeAbort()

    /** 16 kHz mono samples in, text out. Null when it failed or was cancelled. */
    fun transcribe(modelPath: String, audio: FloatArray, lang: String, prompt: String, extraAccuracy: Boolean = false,
                   toEnglish: Boolean = false): String? = synchronized(lock) {
        if (!ensureLoaded(modelPath)) return null
        // Extra accuracy: the full window and 3 parallel guesses (slower). Otherwise quick mode.
        // The large models need the full window: a shortened one makes them write nonsense.
        val large = modelPath.contains("large")
        val audioCtx = if (extraAccuracy || large) 1500 else audioCtxFor(audio.size)
        val beams = if (extraAccuracy) 3 else 1
        val bytes = nativeTranscribe(ctx, audio, lang, prompt, threads, audioCtx, beams, toEnglish) ?: return null
        val text = String(bytes, Charsets.UTF_8)
        // Went wrong (numbers, symbols, one word over and over): once more, carefully, without the word hints.
        if (looksBroken(text) && !extraAccuracy) {
            val again = nativeTranscribe(ctx, audio, lang, "", threads, 1500, 3, toEnglish)?.let { String(it, Charsets.UTF_8) }
            if (again != null && !looksBroken(again)) return again
        }
        text
    }
}
