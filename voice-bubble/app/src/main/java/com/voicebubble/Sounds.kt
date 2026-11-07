package com.voicebubble

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Little start/stop sounds, made on the fly (no audio files). "chime" = soft bells, "blip" = retro game blips. */
object Sounds {
    private const val RATE = 22050

    fun start(kind: String) = play(kind, up = true)
    fun stop(kind: String) = play(kind, up = false)

    private fun play(kind: String, up: Boolean) {
        if (kind == "off") return
        Thread {
            runCatching {
                val notes = if (kind == "blip") listOf(if (up) 988.0 else 659.0, if (up) 1319.0 else 494.0)
                    else listOf(if (up) 784.0 else 1047.0, if (up) 1047.0 else 784.0)
                val noteLen = if (kind == "blip") 0.045 else 0.09
                val pcm = synth(notes, noteLen, square = kind == "blip")
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((pcm.size * 1000L / RATE) + 60)
                track.release()
            }
        }.start()
    }

    private fun synth(freqs: List<Double>, seconds: Double, square: Boolean): ShortArray {
        val per = (RATE * seconds).toInt()
        val out = ShortArray(per * freqs.size)
        freqs.forEachIndexed { n, f ->
            for (i in 0 until per) {
                val t = i.toDouble() / RATE
                val wave = sin(2 * PI * f * t).let { if (square) (if (it >= 0) 0.6 else -0.6) else it }
                // Quick fade in/out so it doesn't click.
                val env = minOf(1.0, i / (RATE * 0.004)) * minOf(1.0, (per - i) / (RATE * 0.02))
                out[n * per + i] = (wave * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}
