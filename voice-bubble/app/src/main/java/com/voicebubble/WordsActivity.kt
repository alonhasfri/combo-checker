package com.voicebubble

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Dictionary: names and words it should spell your way. Anything that sounds close is written like this.
 * Shortcuts: say "btw", get "by the way".
 * Import: from a screenshot of another app, or a code Claude writes for you from those screenshots.
 */
class WordsActivity : BaseActivity() {

    private lateinit var dictList: LinearLayout
    private lateinit var shortList: LinearLayout
    private lateinit var importBox: EditText
    private lateinit var tryOut: TextView
    private lateinit var tryIn: EditText

    private val pickImage = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page { col ->
            col.addView(text("Dictionary & shortcuts", 24f, bold = true))

            // Dictionary
            val cd = card(col, "Dictionary")
            cd.addView(text("Names and words it should spell your way. Anything you say that sounds close is written like this.",
                13f, color = grey))
            val wordIn = field("e.g. Comboed")
            cd.addView(row(wordIn, button("Add") {
                val w = wordIn.text.toString().trim()
                if (w.isEmpty()) return@button
                Words.saveDictionary(this, Words.dictionary(this) + w)
                wordIn.setText("")
                refresh()
            }))
            dictList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cd.addView(dictList)

            // Shortcuts
            val cs = card(col, "Shortcuts")
            cs.addView(text("Say the short version, get the long one.", 13f, color = grey))
            val sayIn = field("When I say… (e.g. btw)")
            val writeIn = field("Write… (e.g. by the way)").apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            cs.addView(sayIn)
            cs.addView(writeIn)
            cs.addView(button("Add shortcut") {
                val s = sayIn.text.toString().trim()
                val w = writeIn.text.toString()
                if (s.isEmpty() || w.isBlank()) return@button
                Words.saveShortcuts(this, Words.shortcuts(this).filter { Words.norm(it.say) != Words.norm(s) } + Shortcut(s, w))
                sayIn.setText(""); writeIn.setText("")
                refresh()
            })
            shortList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cs.addView(shortList)

            // Try
            val ct = card(col, "Test it")
            ct.addView(text("Type a sentence the way it might be heard, to see what you'd get.", 13f, color = grey))
            tryIn = field("e.g. btw I opened komboed")
            tryOut = text("", 15f).apply { setPadding(0, dp(6), 0, 0) }
            tryIn.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) = updateTry()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            ct.addView(tryIn)
            ct.addView(tryOut)

            // Import
            val ci = card(col, "Bring them from another app")
            ci.addView(text("Option A (best): let Claude read your screenshots.\n" +
                    "1. Take screenshots of the other app's dictionary and shortcuts.\n" +
                    "2. Tap \"Copy prompt for Claude\", open Claude, paste the prompt and attach the screenshots.\n" +
                    "3. Copy the code Claude writes, paste it in the box below and tap Import.",
                13f, color = grey))
            ci.addView(button("Copy prompt for Claude") {
                copy(Words.CLAUDE_PROMPT)
                toast("Prompt copied. Paste it in Claude with your screenshots")
            })
            ci.addView(button("Open Claude") { openClaude() })
            ci.addView(text("\nOption B: read a screenshot here, on the phone (works best for English lists). " +
                    "You can also share a screenshot to Voice Bubble from your gallery.", 13f, color = grey))
            ci.addView(button("Read a screenshot") {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), pickImage)
            })
            importBox = field("Paste the code or list here").apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 4
                gravity = Gravity.TOP
            }
            ci.addView(importBox)
            ci.addView(button("Import") { doImport() })

            // Export
            val ce = card(col, "Back up or move to another phone")
            ce.addView(text("Copies all your words and shortcuts as a code. Paste it into Import on the other phone.",
                13f, color = grey))
            ce.addView(button("Copy my code") {
                copy(Words.export(this))
                toast("Copied")
            })
        }
        refresh()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A screenshot or text shared to Voice Bubble from another app. */
    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val txt = i.getStringExtra(Intent.EXTRA_TEXT)
        when {
            uri != null && (i.type ?: "").startsWith("image/") -> readScreenshot(uri)
            txt != null -> { importBox.setText(txt); toast("Check the list, then tap Import") }
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
            val cur = importBox.text.toString().trim()
            importBox.setText(if (cur.isEmpty()) out else "$cur\n$out")
            toast("Check the list: delete lines that aren't words, then tap Import")
        }
    }

    private fun doImport() {
        val p = Words.parse(importBox.text.toString())
        if (p.words.isEmpty() && p.shorts.isEmpty()) { toast("Nothing to import found"); return }
        val preview = buildString {
            if (p.words.isNotEmpty()) append("Words (${p.words.size}): ").append(p.words.take(12).joinToString(", "))
                .append(if (p.words.size > 12) "…" else "").append("\n\n")
            if (p.shorts.isNotEmpty()) append("Shortcuts (${p.shorts.size}):\n")
                .append(p.shorts.take(8).joinToString("\n") { "${it.say} → ${it.write.replace("\n", " ⏎ ")}" })
                .append(if (p.shorts.size > 8) "\n…" else "")
        }
        AlertDialog.Builder(this).setTitle("Import these?").setMessage(preview)
            .setPositiveButton("Import") { _, _ ->
                val (w, s) = Words.merge(this, p)
                importBox.setText("")
                refresh()
                toast("Added $w words and $s shortcuts")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openClaude() {
        val app = packageManager.getLaunchIntentForPackage("com.anthropic.claude")
        runCatching { startActivity(app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new"))) }
    }

    private fun refresh() {
        dictList.removeAllViews()
        val dict = Words.dictionary(this)
        if (dict.isEmpty()) dictList.addView(text("No words yet.", 14f, color = grey).apply { setPadding(0, dp(6), 0, 0) })
        dict.forEach { w ->
            dictList.addView(item(w, onEdit = { editWord(w) }, onDelete = {
                Words.saveDictionary(this, Words.dictionary(this).filter { it != w }); refresh()
            }))
        }
        shortList.removeAllViews()
        val sc = Words.shortcuts(this)
        if (sc.isEmpty()) shortList.addView(text("No shortcuts yet.", 14f, color = grey).apply { setPadding(0, dp(6), 0, 0) })
        sc.forEach { s ->
            shortList.addView(item("${s.say}  →  ${s.write.replace("\n", " ⏎ ")}", onEdit = { editShortcut(s) }, onDelete = {
                Words.saveShortcuts(this, Words.shortcuts(this).filter { it != s }); refresh()
            }))
        }
        updateTry()
    }

    private fun updateTry() {
        if (!::tryIn.isInitialized) return
        val t = tryIn.text.toString()
        tryOut.text = if (t.isBlank()) "" else "→ " + Words.apply(this, t)
    }

    private fun editWord(old: String) {
        val f = field("").apply { setText(old) }
        AlertDialog.Builder(this).setTitle("Edit word").setView(padded(f))
            .setPositiveButton("Save") { _, _ ->
                val n = f.text.toString().trim()
                Words.saveDictionary(this, Words.dictionary(this).map { if (it == old) n else it }); refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun editShortcut(old: Shortcut) {
        val say = field("When I say…").apply { setText(old.say) }
        val write = field("Write…").apply {
            setText(old.write)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(say); addView(write) }
        AlertDialog.Builder(this).setTitle("Edit shortcut").setView(padded(box))
            .setPositiveButton("Save") { _, _ ->
                val n = Shortcut(say.text.toString().trim(), write.text.toString())
                Words.saveShortcuts(this, Words.shortcuts(this).map { if (it == old) n else it }); refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // --- small helpers ---

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        textSize = 15f
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun row(field: EditText, btn: android.widget.Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(btn)
    }

    private fun item(label: String, onEdit: () -> Unit, onDelete: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(2), 0, dp(2))
        val t = text(label, 15f).apply {
            setPadding(dp(2), dp(10), dp(8), dp(10))
            setOnClickListener { onEdit() }
        }
        addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("✕", 18f, color = Color.rgb(200, 60, 60)).apply {
            setPadding(dp(14), dp(8), dp(6), dp(8))
            setOnClickListener {
                AlertDialog.Builder(this@WordsActivity).setMessage("Delete \"$label\"?")
                    .setPositiveButton("Delete") { _, _ -> onDelete() }
                    .setNegativeButton("Cancel", null).show()
            }
        })
    }

    private fun padded(v: android.view.View) = LinearLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun copy(s: String) =
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", s))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
