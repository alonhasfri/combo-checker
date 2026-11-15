package com.voicebubble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.TextView
import java.io.File

/**
 * Voice notes and recordings to text, of any length (an hour is fine): share a voice message to Voice Bubble,
 * or pick an audio file. It's written out on your phone, piece by piece, and can be translated.
 */
class VoiceNoteActivity : Screen() {
    companion object {
        @Volatile private var result: String? = null
        @Volatile private var status: String? = null
        @Volatile private var busy = false
        @Volatile private var stopAsked = false
        /** 0 = write it out, 1 = English translation (on the phone). */
        @Volatile private var mode = 0
        @Volatile private var anyLanguage = false
        @Volatile private var savedFile: File? = null
        private var languageSet = false
    }

    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (!languageSet) { anyLanguage = !Models.byId(Prefs(this).modelId).englishOnly; languageSet = true }
        @Suppress("DEPRECATION")
        val uri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
        if (uri != null && savedInstanceState == null) transcribe(uri)
    }

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Recording to text"))
        page.addView(Ui.note(this, "Share a voice message or recording to Voice Bubble (in WhatsApp: hold it → Share → " +
                "Voice Bubble), or pick an audio file. Any length works, even an hour. It's written out on your phone, " +
                "nothing is uploaded.").apply { setPaddingRelative(dp(4), dp(14), dp(4), 0) })
        if (!busy) {
            page.addView(Ui.segmented(this, listOf("Write it out", "Translate to English"), mode) { i -> mode = i; render() }, Ui.lp(this, 14))
            if (mode == 0) page.addView(Ui.segmented(this, listOf("English", "Any language"), if (anyLanguage) 1 else 0) { i -> anyLanguage = i == 1; render() }, Ui.lp(this, 10))
            else page.addView(Ui.note(this, "Speech in any language becomes English text. Needs an “all languages” model.").apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })
            page.addView(Ui.primary(this, "Pick an audio file", R.drawable.ui_upload) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*"), 7)
            }, Ui.lp(this, 14))
        } else {
            page.addView(Ui.secondary(this, "Stop", R.drawable.ui_x) { stopAsked = true; say("Stopping after this piece…") }, Ui.lp(this, 14))
            page.addView(Ui.note(this, "Keep Voice Bubble open while it works (the screen stays on).").apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })
        }
        statusView = Ui.note(this, status ?: "").also { page.addView(it, Ui.lp(this, 12)) }
        result?.let { text ->
            page.addView(Ui.paddedCard(this).apply {
                addView(Ui.text(this@VoiceNoteActivity, if (text.length > 20000) text.take(20000) + "\n\n… (the rest is in Copy / Share)" else text,
                    16f, p.ink, bold = false).apply { setTextIsSelectable(true); setLineSpacing(0f, 1.25f) })
            }, Ui.lp(this, 12))
            page.addView(Ui.primary(this, "Copy", R.drawable.ui_file) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Recording", text))
                toast("Copied")
            }, Ui.lp(this, 12))
            page.addView(Ui.secondary(this, "Share", R.drawable.ui_upload) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share text"))
            }, Ui.lp(this, 10))
            if (!busy && Ai.hasKey(this)) page.addView(Ui.secondary(this, "Translate with AI…", R.drawable.ui_globe) { aiTranslate(text) }, Ui.lp(this, 10))
        }
        Ui.plainScreen(this, page)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) data?.data?.let { transcribe(it) }
    }

    private fun say(s: String) { status = s; runOnUiThread { statusView?.text = s } }

    private fun setBusy(on: Boolean) {
        busy = on
        runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isFinishing) render()
        }
    }

    private fun pickModel(): Model? {
        val have = Models.all.filter { Models.isDownloaded(this, it) }
        return when {
            // Translating: an all-languages model that can translate (the large turbo one isn't good at it).
            mode == 1 -> have.filter { !it.englishOnly }.sortedBy { if (it.id.startsWith("large")) 1 else 0 }.firstOrNull()
            anyLanguage -> have.firstOrNull { !it.englishOnly }
            else -> Models.byId(Prefs(this).modelId).takeIf { it in have } ?: have.firstOrNull()
        }
    }

    private fun transcribe(uri: Uri) {
        if (busy) { toast("Still working on the last one"); return }
        val model = pickModel() ?: run {
            toast(if (mode == 1 || anyLanguage) "Download an \"all languages\" model first (Home → Speech model)" else "Download a speech model first (Home → Speech model)")
            return
        }
        stopAsked = false; result = null
        setBusy(true)
        val app = applicationContext
        val lang = if (mode == 1 || anyLanguage) "auto" else "en"
        val translate = mode == 1
        Thread {
            // Saved as it goes, so a long one isn't lost if something stops it.
            val file = File(File(app.filesDir, "recordings").apply { mkdirs() }, "recording-${System.currentTimeMillis()}.txt")
            savedFile = file
            try {
                val total = AudioFile.durationSec(app, uri)
                say("Opening the recording…")
                val sb = StringBuilder()
                var done = 0
                val t0 = System.currentTimeMillis()
                val path = Models.file(app, model).path
                AudioFile.stream(app, uri) { piece ->
                    val trimmed = Audio.trimSilence(piece)
                    if (trimmed.size > 16000 / 2) {
                        val prev = sb.takeLast(200).toString()
                        Whisper.transcribe(path, trimmed, lang, (Words.prompt(app) + " " + prev).trim(), toEnglish = translate)
                            ?.let { clean(it) }?.takeIf { it.isNotBlank() && !looksBroken(it) }
                            ?.let { t -> if (sb.isNotEmpty()) sb.append(' '); sb.append(t); file.appendText(t + " ") }
                    }
                    done += piece.size / 16000
                    // How far, and about how long is left.
                    val spent = (System.currentTimeMillis() - t0) / 1000
                    val left = if (total > 0 && done > 0) ((total - done).coerceAtLeast(0) * spent / done) else -1
                    say("Writing it out… ${clock(done)}${if (total > 0) " of ${clock(total)}" else ""}" +
                        (if (left > 0) " · about ${clock(left.toInt())} left" else ""))
                    result = sb.toString()
                    !stopAsked
                }
                val text = Words.apply(app, sb.toString().trim())
                result = text.ifEmpty { null }
                file.writeText(text)
                say(when {
                    text.isEmpty() -> "Didn't hear any words in it."
                    stopAsked -> "Stopped at ${clock(done)}. What was written so far is below."
                    else -> "Done · ${clock(done)} of audio in ${clock(((System.currentTimeMillis() - t0) / 1000).toInt())}"
                })
            } catch (e: Exception) {
                say("Couldn't open that recording (${e.message ?: "unknown problem"}).")
            } finally { setBusy(false) }
        }.start()
    }

    private fun clock(sec: Int) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

    /** Any language, with your AI key: done in parts, so a long text works too. */
    private fun aiTranslate(text: String) {
        val f = Ui.field(this, "Language, e.g. Hebrew, Spanish")
        Ui.dialog(this).setTitle("Translate into…").setView(Ui.padded(this, f)).setPositiveButton("Translate") { _, _ ->
            val language = f.text.toString().trim().ifEmpty { return@setPositiveButton }
            setBusy(true)
            val app = applicationContext
            Thread {
                try {
                    // Parts of about 1500 characters, split between sentences.
                    val parts = ArrayList<String>()
                    var cur = StringBuilder()
                    for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
                        if (cur.length + sentence.length > 1500 && cur.isNotEmpty()) { parts += cur.toString(); cur = StringBuilder() }
                        if (cur.isNotEmpty()) cur.append(' ')
                        cur.append(sentence)
                    }
                    if (cur.isNotEmpty()) parts += cur.toString()
                    val out = StringBuilder()
                    for ((i, part) in parts.withIndex()) {
                        if (stopAsked) break
                        say("Translating into $language… part ${i + 1} of ${parts.size}")
                        out.append(Ai.translate(app, part, language)).append("\n\n")
                    }
                    result = out.toString().trim()
                    say("Translated into $language")
                } catch (e: Exception) {
                    say("Translation stopped: ${e.message ?: "unknown problem"}")
                } finally { stopAsked = false; setBusy(false) }
            }.start()
        }.setNegativeButton("Cancel", null).show()
    }
}
