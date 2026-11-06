package com.voicebubble

/** On-device Whisper speech recognition (whisper.cpp). Keeps one model loaded in memory. */
object Whisper {
    init { System.loadLibrary("voicebubble") }

    private external fun nativeLoad(path: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeAbort()
    private external fun nativeTranscribe(ptr: Long, audio: FloatArray, lang: String, prompt: String, threads: Int): ByteArray?

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

    /** Stops a transcription that is running on another thread. */
    fun abort() = nativeAbort()

    /** 16 kHz mono samples in, text out. Null when it failed or was cancelled. */
    fun transcribe(modelPath: String, audio: FloatArray, lang: String, prompt: String): String? = synchronized(lock) {
        if (!ensureLoaded(modelPath)) return null
        // Big phone cores do the work; spreading over all 8 (incl. the slow ones) is usually slower.
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val bytes = nativeTranscribe(ctx, audio, lang, prompt, threads) ?: return null
        String(bytes, Charsets.UTF_8)
    }
}
