package com.voicebubble

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout

/** Dictionary tab: names and words it should spell your way. */
class DictionaryActivity : Screen() {

    private var query = ""
    private var list: LinearLayout? = null

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.header(this, "Dictionary", gear = true, sub = "Names and words it should spell your way"))

        // Search + add
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = Ui.field(this, "Search").apply {
            setText(query)
            setCompoundDrawablesRelative(getDrawable(R.drawable.ui_search)?.mutate()?.apply {
                setTint(p.muted); setBounds(0, 0, dp(18), dp(18))
            }, null, null, null)
            compoundDrawablePadding = dp(10)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { query = e?.toString() ?: ""; fillList() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        bar.addView(search, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        bar.addView(Ui.button(this, "Add", R.drawable.ui_plus, p.accent, 0xFFFFFFFF.toInt(), heightDp = 48) { editWord(null) },
            LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { marginStart = dp(10) })
        page.addView(bar, Ui.lp(this, 16))

        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = l
        page.addView(l, Ui.lp(this, 14))
        fillList()

        page.addView(Ui.sectionTitle(this, "Bring your words"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_upload, "purple", "Import from another app", sub = "Screenshots, Claude, or a code") {
                startActivity(Intent(this, ImportActivity::class.java))
            },
            Ui.row(this, R.drawable.ui_copy, "teal", "Copy my code", sub = "Backup, or move to another phone") {
                copy(Words.export(this)); toast("Copied your words and shortcuts")
            },
        )))
        page.addView(Ui.note(this, "Anything you say that sounds close to a word here is written like this. " +
                "Short words must match exactly; longer ones may be a letter or two off.").apply { setPaddingRelative(dp(4), dp(10), dp(4), 0) })

        Ui.tabScreen(this, page, "dictionary")
    }

    private fun fillList() {
        val l = list ?: return
        l.removeAllViews()
        val all = Words.dictionary(this)
        val q = query.trim()
        val shown = if (q.isEmpty()) all else all.filter { it.contains(q, ignoreCase = true) }
        if (all.isEmpty()) {
            l.addView(Ui.paddedCard(this).apply {
                addView(Ui.tile(this@DictionaryActivity, R.drawable.ui_book, "blue", 40, 22))
                addView(Ui.text(this@DictionaryActivity, "No words yet", 17f, p.ink).apply { setPaddingRelative(0, dp(10), 0, 0) })
                addView(Ui.note(this@DictionaryActivity, "Add names, places and words it gets wrong, like \"Comboed\" or a friend's name. " +
                        "Or import your list from another dictation app."))
            })
            return
        }
        l.addView(Ui.note(this, if (q.isEmpty()) "${all.size} words" else "${shown.size} of ${all.size} words")
            .apply { setPaddingRelative(dp(4), 0, 0, dp(8)) })
        if (shown.isEmpty()) return
        val card = Ui.card(this)
        shown.forEachIndexed { i, w ->
            if (i > 0) card.addView(Ui.divider(this, 16))
            card.addView(Ui.text(this, w, 16f, p.ink, bold = false).apply {
                setPaddingRelative(dp(16), dp(15), dp(16), dp(15))
                isClickable = true; Ui.ripple(this@DictionaryActivity, this)
                setOnClickListener { editWord(w) }
            })
        }
        l.addView(card)
    }

    /** Add (old == null) or edit a word. */
    private fun editWord(old: String?) {
        val f: EditText = Ui.field(this, "e.g. Comboed").apply { setText(old ?: query) }
        val b = Ui.dialog(this).setTitle(if (old == null) "Add word" else "Edit word").setView(Ui.padded(this, f))
            .setPositiveButton("Save") { _, _ ->
                val n = f.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                val cur = Words.dictionary(this)
                Words.saveDictionary(this, if (old == null) cur + n else cur.map { if (it == old) n else it })
                query = ""
                render()
            }
            .setNegativeButton("Cancel", null)
        if (old != null) b.setNeutralButton("Delete") { _, _ ->
            Words.saveDictionary(this, Words.dictionary(this).filter { it != old }); render()
        }
        b.show()
        f.requestFocus()
    }
}
