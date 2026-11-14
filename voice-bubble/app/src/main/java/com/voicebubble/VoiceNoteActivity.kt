package com.voicebubble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.TextView

/**
 * Voice notes to text: share a voice message (WhatsApp, Telegram…) to Voice Bubble, or pick an audio
 * file, and it's written out on your phone, with nothing sent anywhere.
 */
class VoiceNoteActivity : Screen() {
    companion object {
        @Volatile private var result: String? = null
        @Volatile private var status: String? = null
        @Volatile private var busy = false
    }

    private var statusView: TextView? = null
    private var anyLanguage = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        anyLanguage = !Models.byId(Prefs(this).modelId).englishOnly
        // Shared from another app.
        @Suppress("DEPRECATION")
        val uri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
        if (uri != null && savedInstanceState == null) transcribe(uri)
    }

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Voice note to text"))
        page.addView(Ui.note(this, "Share a voice message to Voice Bubble (in WhatsApp: hold the voice note → Share → " +
                "Voice Bubble), or pick an audio file. It's written out on your phone.").apply { setPaddingRelative(dp(4), dp(14), dp(4), 0) })
        page.addView(Ui.segmented(this, listOf("English", "Any language"), if (anyLanguage) 1 else 0) { i -> anyLanguage = i == 1; render() }, Ui.lp(this, 14))
        page.addView(Ui.primary(this, "Pick an audio file", R.drawable.ui_upload) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*"), 7)
        }, Ui.lp(this, 14))
        statusView = Ui.note(this, status ?: "").also { page.addView(it, Ui.lp(this, 12)) }
        result?.let { text ->
            page.addView(Ui.paddedCard(this).apply {
                addView(Ui.text(this@VoiceNoteActivity, text, 16f, p.ink, bold = false).apply { setTextIsSelectable(true); setLineSpacing(0f, 1.25f) })
            }, Ui.lp(this, 12))
            page.addView(Ui.primary(this, "Copy", R.drawable.ui_file) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice note", text))
                toast("Copied")
            }, Ui.lp(this, 12))
            page.addView(Ui.secondary(this, "Share", R.drawable.ui_upload) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share text"))
            }, Ui.lp(this, 10))
        }
        Ui.plainScreen(this, page)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) data?.data?.let { transcribe(it) }
    }

    private fun say(s: String) { status = s; runOnUiThread { statusView?.text = s } }

    private fun transcribe(uri: Uri) {
        if (busy) { toast("Still working on the last one"); return }
        val prefs = Prefs(this)
        // English: your model. Any language: an all-languages model.
        val model = if (anyLanguage) Models.all.firstOrNull { !it.englishOnly && Models.isDownloaded(this, it) }
            else Models.byId(prefs.modelId).takeIf { Models.isDownloaded(this, it) } ?: Models.all.firstOrNull { Models.isDownloaded(this, it) }
        if (model == null) {
            toast(if (anyLanguage) "Download an \"all languages\" model first (Home → Speech model)" else "Download a speech model first (Home → Speech model)")
            return
        }
        busy = true; result = null
        render()
        val app = applicationContext
        val lang = if (anyLanguage) "auto" else "en"
        Thread {
            try {
                say("Opening the voice note…")
                val audio = AudioFile.decode(app, uri)
                val total = audio.size / 16000
                val parts = ArrayList<String>()
                val step = 16000 * 28
                var at = 0
                while (at < audio.size) {
                    say("Writing it out… ${at / 16000}s of ${total}s")
                    val piece = Audio.trimSilence(audio.copyOfRange(at, minOf(audio.size, at + step)))
                    val prev = parts.lastOrNull().orEmpty().takeLast(200)
                    if (piece.size > 16000 / 2) Whisper.transcribe(Models.file(app, model).path, piece, lang,
                        (Words.prompt(app) + " " + prev).trim())?.let { parts += clean(it) }
                    at += step
                }
                val text = Words.apply(app, parts.filter { it.isNotBlank() }.joinToString(" ").trim())
                result = text.ifEmpty { null }
                say(if (text.isEmpty()) "Didn't hear any words in it." else "Done · ${total}s of audio")
            } catch (e: Exception) {
                say("Couldn't open that voice note (${e.message ?: "unknown problem"}).")
            } finally {
                busy = false
                runOnUiThread { if (!isFinishing) render() }
            }
        }.start()
    }
}
