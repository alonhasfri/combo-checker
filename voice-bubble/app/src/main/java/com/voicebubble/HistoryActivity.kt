package com.voicebubble

import android.content.ClipData
import android.content.ClipboardManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout

/** Everything you dictated (as far back as history goes), with a search box. Tap to copy, hold to delete. */
class HistoryActivity : Screen() {
    private var query = ""
    private var list: LinearLayout? = null

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "History"))
        val search = Ui.field(this, "Search what you said…").apply {
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s.toString(); fill() }
            })
        }
        page.addView(search, Ui.lp(this, 16))
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = l
        page.addView(l, Ui.lp(this, 14))
        fill()
        Ui.plainScreen(this, page)
    }

    private fun fill() {
        val l = list ?: return
        l.removeAllViews()
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val found = Stats.history(this).filter { e -> words.all { e.text.lowercase().contains(it) } }
        if (!Stats.historyOn(this)) { l.addView(Ui.note(this, "History is off (Settings → Privacy).")); return }
        if (found.isEmpty()) { l.addView(Ui.note(this, if (words.isEmpty()) "Nothing dictated yet." else "Nothing matches “$query”.")); return }
        l.addView(Ui.note(this, "${found.size} found · tap to copy, hold to delete").apply { setPaddingRelative(dp(6), 0, 0, dp(8)) })
        found.take(200).forEach { e ->
            val card = Ui.paddedCard(this).apply {
                addView(Ui.text(this@HistoryActivity, e.text, 15f, p.ink, bold = false).apply { setLineSpacing(0f, 1.2f) })
                addView(Ui.text(this@HistoryActivity, Stats.ago(e.time), 12f, p.muted, bold = false), Ui.lp(this@HistoryActivity, 6))
                isClickable = true
                Ui.ripple(this@HistoryActivity, this)
                setOnClickListener {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", e.text))
                    toast("Copied")
                }
                setOnLongClickListener {
                    Ui.dialog(this@HistoryActivity).setMessage("Delete this from your history?")
                        .setPositiveButton("Delete") { _, _ -> Stats.deleteEntry(this@HistoryActivity, e); fill() }
                        .setNegativeButton("Cancel", null).show()
                    true
                }
            }
            l.addView(card, Ui.lp(this, 10))
        }
    }
}
