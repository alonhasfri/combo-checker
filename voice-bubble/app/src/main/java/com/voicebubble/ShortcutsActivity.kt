package com.voicebubble

import android.content.Intent
import android.widget.LinearLayout

/** Shortcuts tab: say "btw", get "by the way". */
class ShortcutsActivity : Screen() {

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.header(this, "Shortcuts", gear = true, sub = "Say the short version, get the long one"))
        page.addView(Ui.primary(this, "New shortcut", R.drawable.ui_plus) { edit(null) }, Ui.lp(this, 16))

        val all = Words.shortcuts(this)
        if (all.isEmpty()) {
            page.addView(Ui.paddedCard(this).apply {
                addView(Ui.tile(this@ShortcutsActivity, R.drawable.ui_zap, "orange", 40, 22))
                addView(Ui.text(this@ShortcutsActivity, "No shortcuts yet", 17f, p.ink).apply { setPaddingRelative(0, dp(10), 0, 0) })
                addView(Ui.note(this@ShortcutsActivity, "Examples: say \"btw\" → \"by the way\", \"my email\" → your address, " +
                        "\"sig\" → a whole signature. Shortcuts can be several lines long."))
            }, Ui.lp(this, 14))
        } else {
            page.addView(Ui.note(this, "${all.size} shortcuts").apply { setPaddingRelative(dp(4), dp(14), 0, dp(8)) })
            val card = Ui.card(this)
            all.forEachIndexed { i, s ->
                if (i > 0) card.addView(Ui.divider(this, 16))
                card.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPaddingRelative(dp(16), dp(12), dp(16), dp(12))
                    addView(Ui.text(this@ShortcutsActivity, s.say, 16f, p.accentInk))
                    addView(Ui.text(this@ShortcutsActivity, s.write, 14f, p.ink, bold = false).apply {
                        maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                        setPaddingRelative(0, dp(2), 0, 0)
                    })
                    isClickable = true; Ui.ripple(this@ShortcutsActivity, this)
                    setOnClickListener { edit(s) }
                })
            }
            page.addView(card)
        }

        page.addView(Ui.sectionTitle(this, "Bring your shortcuts"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_upload, "purple", "Import from another app", sub = "Screenshots, Claude, or a code") {
                startActivity(Intent(this, ImportActivity::class.java))
            },
            Ui.row(this, R.drawable.ui_copy, "teal", "Copy my code", sub = "Backup, or move to another phone") {
                copy(Words.export(this)); toast("Copied your words and shortcuts")
            },
        )))
        page.addView(Ui.note(this, "Also catches it when you spell it out (\"B.T.W.\"). At the start of a sentence the first letter is capitalised.")
            .apply { setPaddingRelative(dp(4), dp(10), dp(4), 0) })

        Ui.tabScreen(this, page, "shortcuts")
    }

    private fun edit(old: Shortcut?) {
        val say = Ui.field(this, "When I say… (e.g. btw)").apply { setText(old?.say ?: "") }
        val write = Ui.field(this, "Write… (e.g. by the way)", multiLine = true).apply { setText(old?.write ?: ""); minLines = 2 }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(say)
            addView(write, Ui.lp(this@ShortcutsActivity, 10))
        }
        val b = Ui.dialog(this).setTitle(if (old == null) "New shortcut" else "Edit shortcut").setView(Ui.padded(this, box))
            .setPositiveButton("Save") { _, _ ->
                val s = say.text.toString().trim()
                val w = write.text.toString()
                if (s.isEmpty() || w.isBlank()) { toast("Fill in both boxes"); return@setPositiveButton }
                val rest = Words.shortcuts(this).filter { it != old && Words.norm(it.say) != Words.norm(s) }
                Words.saveShortcuts(this, rest + Shortcut(s, w))
                render()
            }
            .setNegativeButton("Cancel", null)
        if (old != null) b.setNeutralButton("Delete") { _, _ ->
            Words.saveShortcuts(this, Words.shortcuts(this).filter { it != old }); render()
        }
        b.show()
        say.requestFocus()
    }
}
