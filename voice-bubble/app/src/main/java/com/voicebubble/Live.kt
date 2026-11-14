package com.voicebubble

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * The "Live" engine: streaming speech recognition (sherpa-onnx). It turns speech into text while you
 * talk, word by word, so when you tap stop the text is already done. English only; Whisper stays for
 * other languages, translation, and as a backup.
 */
object Live {
    class LiveModel(
        val id: String, val title: String, val detail: String, val repo: String,
        val encoder: String, val decoder: String, val joiner: String,
        /** File name → exact size in bytes (to know a download finished). */
        val files: List<Pair<String, Long>>,
    ) {
        val bytes get() = files.sumOf { it.second }
        fun url(file: String) = "https://huggingface.co/csukuangfj/$repo/resolve/main/$file"
    }

    val models = listOf(
        LiveModel("kroko", "Live · English", "Kroko streaming · 71 MB · types as you talk, fast",
            "sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06", "encoder.onnx", "decoder.onnx", "joiner.onnx",
            listOf("encoder.onnx" to 70_092_599L, "decoder.onnx" to 617_488L, "joiner.onnx" to 336_817L, "tokens.txt" to 6_310L)),
        LiveModel("nemotron", "Live+ · English (most accurate)",
            "NVIDIA Nemotron streaming 0.6B · 662 MB · best results, needs a fast phone",
            "sherpa-onnx-nemotron-speech-streaming-en-0.6b-int8-2026-01-14", "encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx",
            listOf("encoder.int8.onnx" to 652_916_830L, "decoder.int8.onnx" to 7_257_753L, "joiner.int8.onnx" to 1_735_862L, "tokens.txt" to 8_952L)),
    )

    fun byId(id: String?) = models.firstOrNull { it.id == id } ?: models[0]
    fun dir(c: Context, m: LiveModel) = File(File(c.filesDir, "live"), m.id).apply { mkdirs() }
    fun file(c: Context, m: LiveModel, name: String) = File(dir(c, m), name)
    fun isDownloaded(c: Context, m: LiveModel) = m.files.all { (n, size) -> file(c, m, n).let { it.exists() && it.length() == size } }

    private val lock = Any()
    private var recognizer: OnlineRecognizer? = null
    private var loadedId: String? = null
    /** Why the last load failed (shown to you), or null. */
    @Volatile var lastError: String? = null

    /** Loads the model (a second or two the first time). False when it can't. */
    fun ensureLoaded(c: Context, m: LiveModel): Boolean = synchronized(lock) {
        if (recognizer != null && loadedId == m.id) return true
        release()
        if (!isDownloaded(c, m)) return false
        return try {
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = file(c, m, m.encoder).path,
                        decoder = file(c, m, m.decoder).path,
                        joiner = file(c, m, m.joiner).path,
                    ),
                    tokens = file(c, m, "tokens.txt").path,
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                ),
                // We decide when you've finished (you tap stop), not the model.
                enableEndpoint = false,
                decodingMethod = "greedy_search",
            )
            recognizer = OnlineRecognizer(null, config)
            loadedId = m.id
            lastError = null
            true
        } catch (e: Throwable) {
            lastError = (e.message ?: e.javaClass.simpleName).take(120)
            recognizer = null
            loadedId = null
            false
        }
    }

    fun release() = synchronized(lock) {
        runCatching { recognizer?.release() }
        recognizer = null
        loadedId = null
    }

    /** One dictation: feed it audio as it comes in, read the text so far, finish at the end. */
    class Session(private val stream: OnlineStream) {
        @Volatile var text = ""
            private set
        /** Finished or cancelled: the stream is gone, ignore late audio. */
        @Volatile private var done = false

        /** Adds new audio and works on it. Returns the text so far. */
        fun feed(samples: FloatArray): String = synchronized(lock) {
            if (done) return text
            val r = recognizer ?: return text
            if (samples.isNotEmpty()) stream.acceptWaveform(samples, 16000)
            while (r.isReady(stream)) r.decode(stream)
            text = r.getResult(stream).text.trim()
            text
        }

        /** No more audio: finishes the last words and returns the full text. */
        fun finish(): String = synchronized(lock) {
            if (done) return text
            done = true
            val r = recognizer ?: return text
            // A little silence lets the model finish the last word.
            stream.acceptWaveform(FloatArray(16000 * 2 / 5), 16000)
            stream.inputFinished()
            while (r.isReady(stream)) r.decode(stream)
            text = r.getResult(stream).text.trim()
            runCatching { stream.release() }
            text
        }

        fun cancel() = synchronized(lock) {
            if (done) return@synchronized
            done = true
            runCatching { stream.release() }
        }
    }

    fun start(): Session? = synchronized(lock) {
        val r = recognizer ?: return null
        Session(r.createStream())
    }
}
