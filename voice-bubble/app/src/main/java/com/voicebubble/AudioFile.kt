package com.voicebubble

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Opens an audio file (a WhatsApp voice note, an mp3, m4a…) as 16 kHz mono sound for Whisper. */
object AudioFile {
    /** At most this many minutes are read. */
    const val MAX_MINUTES = 15

    fun decode(c: Context, uri: Uri): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(c, uri, null)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No sound in that file")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var out = FloatArray(16000 * 60)
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    // Mixed down to one channel.
                    val frames = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                    if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (ch in 0 until channels) sum += if (floatPcm) buf.float else buf.short / 32768f
                        out[n++] = sum / channels
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    if (n > rate * 60 * MAX_MINUTES) break
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        return resample(out.copyOf(n), rate, 16000)
    }

    /** How long the recording is, in seconds (0 when unknown). */
    fun durationSec(c: Context, uri: Uri): Int = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(c, uri, null)
        val d = (0 until ex.trackCount).map { ex.getTrackFormat(it) }
            .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION) ?: 0L
        ex.release(); (d / 1_000_000).toInt()
    }.getOrDefault(0)

    /**
     * Long recordings (an hour or more): read bit by bit and handed over in pieces of up to [pieceSec]
     * seconds (16 kHz mono), each cut at a quiet moment so words aren't split. Only a little is in memory
     * at once. [onPiece] returns false to stop.
     */
    fun stream(c: Context, uri: Uri, pieceSec: Int = 28, onPiece: (FloatArray) -> Boolean) {
        val ex = MediaExtractor()
        ex.setDataSource(c, uri, null)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No sound in that file")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        // Sound at the file's own rate, waiting to be made into a piece.
        var raw = FloatArray(rate * (pieceSec + 5))
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var stop = false

        fun emit(final: Boolean) {
            while (!stop && (n >= rate * pieceSec || (final && n > 0))) {
                val take = if (n >= rate * pieceSec) {
                    // Cut at the quietest 100 ms in the last third, so a word isn't cut in half.
                    val win = rate / 10
                    var best = rate * pieceSec; var bestE = Float.MAX_VALUE
                    var at = rate * pieceSec * 2 / 3
                    while (at + win <= rate * pieceSec) {
                        var e = 0f; for (i in at until at + win) e += raw[i] * raw[i]
                        if (e < bestE) { bestE = e; best = at + win / 2 }
                        at += win / 2
                    }
                    best
                } else n
                val piece = resample(raw.copyOfRange(0, take), rate, 16000)
                System.arraycopy(raw, take, raw, 0, n - take)
                n -= take
                if (!onPiece(piece)) stop = true
            }
        }

        try {
            while (!stop) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    val newRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (newRate != rate && n == 0) raw = FloatArray(newRate * (pieceSec + 5))
                    rate = newRate
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val frames = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                    if (n + frames > raw.size) raw = raw.copyOf(maxOf(raw.size * 2, n + frames))
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (ch in 0 until channels) sum += if (floatPcm) buf.float else buf.short / 32768f
                        raw[n++] = sum / channels
                    }
                    codec.releaseOutputBuffer(o, false)
                    emit(false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { emit(true); break }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
    }

    /** Straight-line resampling: plenty for speech. */
    fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt()
        val ratio = from.toDouble() / to
        return FloatArray(outLen) { i ->
            val pos = i * ratio
            val a = pos.toInt().coerceAtMost(input.size - 1)
            val b = (a + 1).coerceAtMost(input.size - 1)
            val t = (pos - a).toFloat()
            input[a] * (1 - t) + input[b] * t
        }
    }
}
