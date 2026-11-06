package com.voicebubble

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Bring your dictionary and shortcuts from another app: via Claude (reads your screenshots),
 * on the phone from a screenshot, or by pasting a code. Screenshots and text can also be shared here.
 */
class ImportActivity : Screen() {

    private val pickImage = 7
    private var pending = ""         // text waiting in the box (kept across re-renders)
    private var box: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onPause() {
        super.onPause()
        box?.let { pending = it.text.toString() }
    }

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Import"))
        page.addView(Ui.note(this, "Move your dictionary and shortcuts over from another dictation app.")
            .apply { setPaddingRelative(dp(4), dp(14), dp(4), 0) })

        // Option A: Claude
        page.addView(Ui.sectionTitle(this, "Best: let Claude read your screenshots"))
        page.addView(Ui.paddedCard(this).apply {
            addView(step(1, "Take screenshots of the other app's dictionary and shortcuts lists."))
            addView(step(2, "Copy the prompt, open Claude, paste it and attach the screenshots."), Ui.lp(this@ImportActivity, 10))
            addView(step(3, "Copy the code Claude writes and paste it in the box below."), Ui.lp(this@ImportActivity, 10))
            val btns = LinearLayout(this@ImportActivity).apply { orientation = LinearLayout.HORIZONTAL }
            btns.addView(Ui.secondary(this@ImportActivity, "Copy prompt", R.drawable.ui_copy) {
                copy(Words.CLAUDE_PROMPT); toast("Prompt copied")
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            btns.addView(Ui.secondary(this@ImportActivity, "Open Claude", R.drawable.ui_spark) { openClaude() },
                LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { marginStart = dp(10) })
            addView(btns, Ui.lp(this@ImportActivity, 16))
        })

        // Option B: on the phone
        page.addView(Ui.sectionTitle(this, "Or read a screenshot here"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_image, "teal", "Choose a screenshot", sub = "Works best for English lists") {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), pickImage)
            },
        )))
        page.addView(Ui.note(this, "Tip: you can also share a screenshot to Voice Bubble from your gallery.")
            .apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        // Paste + import
        page.addView(Ui.sectionTitle(this, "Paste and import"))
        val f = Ui.field(this, "Paste the code or list here", multiLine = true).apply {
            minLines = 5; setText(pending)
        }
        box = f
        page.addView(f)
        page.addView(Ui.primary(this, "Import", R.drawable.ui_download) { doImport() }, Ui.lp(this, 12))
        page.addView(Ui.note(this, "Lines like \"word: Comboed\" and \"short: btw => by the way\", or a plain list " +
                "(one word per line, \"btw -> by the way\" for shortcuts). You'll see what was found before anything is added.")
            .apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        Ui.plainScreen(this, page)
    }

    private fun step(n: Int, s: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(Ui.text(this@ImportActivity, n.toString(), 13f, p.accentInk).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(p.accentSoft, dp(12).toFloat())
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(Ui.text(this@ImportActivity, s, 14f, p.ink, bold = false).apply { setPaddingRelative(dp(12), dp(2), 0, 0) },
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
    }

    /** A screenshot or text shared to Voice Bubble from another app. */
    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val txt = i.getStringExtra(Intent.EXTRA_TEXT)
        when {
            uri != null && (i.type ?: "").startsWith("image/") -> readScreenshot(uri)
            txt != null -> { pending = txt; box?.setText(txt); toast("Check the list, then tap Import") }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImage && resultCode == RESULT_OK) data?.data?.let { readScreenshot(it) }
    }

    private fun readScreenshot(uri: Uri) {
        toast("Reading the screenshot…")
        ScreenshotReader.read(this, uri) { out ->
            if (out.isNullOrBlank()) { toast("Couldn't read any text in that picture"); return@read }
            val cur = (box?.text?.toString() ?: pending).trim()
            pending = if (cur.isEmpty()) out else "$cur\n$out"
            box?.setText(pending)
            toast("Delete lines that aren't words, then tap Import")
        }
    }

    private fun doImport() {
        val text = box?.text?.toString() ?: pending
        val parsed = Words.parse(text)
        if (parsed.words.isEmpty() && parsed.shorts.isEmpty()) { toast("Nothing to import found"); return }
        val preview = buildString {
            if (parsed.words.isNotEmpty()) append("Words (${parsed.words.size}): ").append(parsed.words.take(15).joinToString(", "))
                .append(if (parsed.words.size > 15) "…" else "").append("\n\n")
            if (parsed.shorts.isNotEmpty()) append("Shortcuts (${parsed.shorts.size}):\n")
                .append(parsed.shorts.take(8).joinToString("\n") { "${it.say} → ${it.write.replace("\n", " ⏎ ")}" })
                .append(if (parsed.shorts.size > 8) "\n…" else "")
        }
        Ui.dialog(this).setTitle("Import these?").setMessage(preview.trim())
            .setPositiveButton("Import") { _, _ ->
                val (w, s) = Words.merge(this, parsed)
                pending = ""
                box?.setText("")
                toast("Added $w words and $s shortcuts")
                render()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openClaude() {
        val app = packageManager.getLaunchIntentForPackage("com.anthropic.claude")
        runCatching { startActivity(app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new"))) }
    }
}
