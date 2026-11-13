package com.voicebubble

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Test your phone: read one sentence aloud, and every speech model you've downloaded writes it out.
 * You see how fast and how right each one is on YOUR phone, and which to pick.
 */
class PhoneTestActivity : Screen() {
    companion object {
        const val SENTENCE = "My friend Sam is meeting me at the park at seven, so please bring the blue ball and some snacks."
        @Volatile private var lines: List<String> = emptyList()
        @Volatile private var best: String? = null
        @Volatile private var status: String? = null
        @Volatile private var busy = false

        /** Share of the sentence's words that came out right (0..1). */
        fun accuracy(heard: String): Float {
            fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            val want = words(SENTENCE)
            val got = words(heard).toMutableList()
            var hit = 0
            for (w in want) { val i = got.indexOf(w); if (i >= 0) { hit++; got.removeAt(i) } }
            return hit.toFloat() / want.size
        }
    }

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Test my phone"))
        page.addView(Ui.note(this, "Tap Start and read this out loud at your normal speed. Every model you've downloaded " +
                "writes it out, so you can see which is fastest and most accurate on this phone.").apply { setPaddingRelative(dp(4), dp(14), dp(4), 0) })
        page.addView(Ui.paddedCard(this).apply {
            addView(Ui.text(this@PhoneTestActivity, "“$SENTENCE”", 17f, p.ink).apply { setLineSpacing(0f, 1.25f) })
        }, Ui.lp(this, 14))
        page.addView(Ui.primary(this, if (busy) "Testing…" else "Start (8 seconds)", R.drawable.ui_mic) { if (!busy) start() }, Ui.lp(this, 14))
        status?.let { page.addView(Ui.note(this, it), Ui.lp(this, 10)) }
        best?.let { page.addView(Ui.text(this, it, 16f, p.good), Ui.lp(this, 12)) }
        if (lines.isNotEmpty()) page.addView(Ui.paddedCard(this).apply {
            lines.forEachIndexed { i, l ->
                addView(Ui.text(this@PhoneTestActivity, l, 14f, p.ink, bold = false).apply { setLineSpacing(0f, 1.2f) }, Ui.lp(this@PhoneTestActivity, if (i == 0) 0 else 12))
            }
        }, Ui.lp(this, 12))
        page.addView(Ui.note(this, "The Live engine isn't in this test: it writes while you talk, so it's always ready the moment you stop.")
            .apply { setPaddingRelative(dp(4), dp(12), dp(4), 0) })
        Ui.plainScreen(this, page)
    }

    private fun say(s: String) { status = s; runOnUiThread { if (!isFinishing) render() } }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Setup.askMic(this); return
        }
        val models = Models.all.filter { Models.isDownloaded(this, it) }
        if (models.isEmpty()) { toast("Download a speech model first"); return }
        busy = true; lines = emptyList(); best = null
        val app = applicationContext
        Thread {
            try {
                // 8 seconds of you reading.
                say("Listening… read the sentence now")
                val rate = 16000
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
                val pcm = ShortArray(rate * 8)
                rec.startRecording()
                var n = 0
                while (n < pcm.size) { val r = rec.read(pcm, n, minOf(2048, pcm.size - n)); if (r <= 0) break; n += r }
                rec.stop(); rec.release()
                val audio = Audio.trimSilence(FloatArray(n) { pcm[it] / 32768f })
                val out = ArrayList<String>()
                var pick: Pair<Model, Float>? = null
                for (m in models) {
                    say("Testing ${m.title}…")
                    val path = Models.file(app, m).path
                    Whisper.transcribe(path, FloatArray(16000), "en", "")   // loads it (not timed)
                    val t0 = System.currentTimeMillis()
                    val text = clean(Whisper.transcribe(path, audio, "en", "") ?: "")
                    val sec = (System.currentTimeMillis() - t0) / 1000f
                    val acc = accuracy(text)
                    out += "${m.title}\n${"%.1f".format(sec)} s · ${(acc * 100).toInt()}% right\n“$text”"
                    lines = out.toList()
                    // Best: the fastest of the ones that got (nearly) everything right.
                    val score = acc * 10 - sec
                    if (acc >= 0.85f && (pick == null || score > pick!!.second)) pick = m to score
                }
                best = pick?.let { "Best for this phone: ${it.first.title}" } ?: "None got it all right: try again somewhere quieter, a bit closer to the phone."
                say("Done")
            } catch (e: Exception) {
                say("The test didn't work (${e.message ?: "unknown problem"})")
            } finally { busy = false; runOnUiThread { if (!isFinishing) render() } }
        }.start()
    }
}
