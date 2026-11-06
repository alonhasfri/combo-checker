package com.voicebubble

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/** Records 16 kHz mono microphone audio (the format Whisper wants) into memory. */
class Recorder {
    companion object {
        const val RATE = 16000
        const val MAX_SECONDS = 300
    }

    @Volatile private var running = false
    @Volatile var level = 0f   // 0..1, for the bubble's pulse
        private set
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var data = ShortArray(RATE * 30)
    private var size = 0
    var onMaxReached: (() -> Unit)? = null

    /** Throws if the microphone can't be opened (no permission, or another app holds it). */
    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, RATE / 2 * 2)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("Microphone not available")
        }
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            throw IllegalStateException("Microphone is busy")
        }
        record = rec
        size = 0
        running = true
        thread = Thread {
            val buf = ShortArray(RATE / 10)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                synchronized(this) {
                    if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
                    System.arraycopy(buf, 0, data, size, n)
                    size += n
                }
                var sum = 0.0
                for (i in 0 until n) { val v = buf[i] / 32768.0; sum += v * v }
                level = (sqrt(sum / n) * 6).toFloat().coerceIn(0f, 1f)
                if (size >= RATE * MAX_SECONDS) { onMaxReached?.invoke(); break }
            }
        }.apply { name = "recorder"; start() }
    }

    val isRecording get() = running

    val seconds: Int get() = synchronized(this) { size / RATE }

    /** Stops and returns the audio as floats in -1..1. */
    fun stop(): FloatArray {
        running = false
        runCatching { thread?.join(500) }
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        thread = null
        level = 0f
        synchronized(this) {
            val out = FloatArray(size)
            for (i in 0 until size) out[i] = data[i] / 32768f
            size = 0
            return out
        }
    }

    fun cancel() { stop() }
}

/** Loudness check so silence isn't sent to Whisper (it tends to invent "Thank you." for silence). */
fun hasSpeech(audio: FloatArray): Boolean {
    if (audio.size < Recorder.RATE / 3) return false
    // Loudest 100 ms window, so a quiet room plus a short phrase still counts.
    val win = Recorder.RATE / 10
    var best = 0.0
    var i = 0
    while (i + win <= audio.size) {
        var s = 0.0
        for (j in i until i + win) s += audio[j] * audio[j]
        best = maxOf(best, s / win)
        i += win
    }
    return sqrt(best) > 0.01
}
