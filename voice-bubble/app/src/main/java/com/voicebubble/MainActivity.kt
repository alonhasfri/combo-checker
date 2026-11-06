package com.voicebubble

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Home: the floating microphone switch, setup checklist, stats, a box to try it,
 * and your recent dictations.
 */
class MainActivity : Screen() {

    private var update: Updates.Release? = null
    private var statusLine: TextView? = null
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { updateStatusLine(); ticker.postDelayed(this, 700) }
    }

    override fun onResume() {
        super.onResume()
        ticker.post(tick)
        Updates.check(this, force = false) { r ->
            runOnUiThread {
                if (!isFinishing && (r?.build != update?.build)) { update = r; render() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ticker.removeCallbacks(tick)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = render()

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.header(this, "Voice Bubble", gear = true, sub = "Speak in any app. Free and on your phone."))

        update?.let { r -> page.addView(updateCard(r), Ui.lp(this, 16)) }
        page.addView(hero(), Ui.lp(this, 16))

        val steps = Setup.steps(this)
        val missing = steps.filter { !it.done }
        if (missing.any { it.required } || (missing.isNotEmpty() && Stats.total(this) == 0)) {
            page.addView(setupCard(steps), Ui.lp(this, 14))
        }

        page.addView(Ui.sectionTitle(this, "Your numbers"))
        page.addView(statsRow())

        page.addView(Ui.sectionTitle(this, "Try it"))
        page.addView(tryCard())

        page.addView(Ui.sectionTitle(this, "Recent"))
        page.addView(recent())

        Ui.tabScreen(this, page, "home")
        updateStatusLine()
    }

    // ----- floating microphone -----

    private fun hero(): View {
        val on = BubbleService.running
        val ready = Setup.readyToStart(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (on) intArrayOf(0xFF3A5BD9.toInt(), 0xFF6A3FD9.toInt()) else intArrayOf(0xFF2A3048.toInt(), 0xFF3A4157.toInt())
            ).apply { cornerRadius = dp(22).toFloat() }
            clipToOutline = true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, "Floating microphone", 14f, 0xCCFFFFFF.toInt()))
        col.addView(Ui.text(this, if (on) "On" else "Off", 30f, 0xFFFFFFFF.toInt()))
        statusLine = Ui.text(this, "", 13f, 0xE6FFFFFF.toInt(), bold = false).apply { setPaddingRelative(0, dp(2), 0, 0) }
        col.addView(statusLine)
        top.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))

        // Big round switch.
        val knob = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (on) 0xFFFFFFFF.toInt() else 0x33FFFFFF)
                setStroke(dp(2), 0x66FFFFFF)
            }
            addView(Ui.icon(this@MainActivity, R.drawable.ui_mic, if (on) 0xFF3A5BD9.toInt() else 0xFFFFFFFF.toInt()),
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            isClickable = true; isFocusable = true
            contentDescription = if (on) "Turn the floating microphone off" else "Turn the floating microphone on"
            Ui.ripple(this@MainActivity, this); clipToOutline = true
            setOnClickListener { toggleBubble() }
        }
        top.addView(knob, LinearLayout.LayoutParams(dp(76), dp(76)))
        card.addView(top)

        card.addView(Ui.button(this, if (on) "Turn off" else if (ready) "Turn on" else "Finish setup to turn on",
            if (on) R.drawable.ui_x else R.drawable.ui_play,
            if (on) 0x33FFFFFF else 0xFFFFFFFF.toInt(), if (on) 0xFFFFFFFF.toInt() else 0xFF2B47B8.toInt(), heightDp = 46) { toggleBubble() },
            Ui.lp(this, 16))
        card.addView(Ui.text(this, "Tap the bubble to talk · tap again to finish · hold to cancel · drag to move",
            12f, 0xBFFFFFFF.toInt(), bold = false).apply { setPaddingRelative(0, dp(10), 0, 0); gravity = Gravity.CENTER })
        return card
    }

    private fun updateStatusLine() {
        val v = statusLine ?: return
        val prefs = Prefs(this)
        v.text = when {
            !BubbleService.running -> if (Setup.readyToStart(this)) "Tap the button to show the bubble" else "A few steps left (see below)"
            !prefs.onlyWithKeyboard -> "Always on screen"
            BubbleService.keyboardNow -> "Keyboard open · bubble showing"
            prefs.keyboardSeen -> "Shows up while you type"
            else -> "Stays on screen until it has seen your keyboard once"
        }
    }

    private fun toggleBubble() {
        if (BubbleService.running) {
            startService(Intent(this, BubbleService::class.java).setAction(BubbleService.ACTION_STOP))
            ticker.postDelayed({ render() }, 300)
            return
        }
        val first = Setup.steps(this).firstOrNull { it.required && !it.done }
        if (first != null) {
            toast(first.title)
            first.fix(this)
            return
        }
        startForegroundService(Intent(this, BubbleService::class.java))
        ticker.postDelayed({ render() }, 300)
    }

    // ----- setup -----

    private fun setupCard(steps: List<Setup.Step>): View {
        val card = Ui.card(this)
        val doneCount = steps.count { it.done }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), dp(14), dp(16), dp(10))
            addView(Ui.text(this@MainActivity, "Get set up", 17f, p.ink))
            addView(Ui.text(this@MainActivity, "$doneCount of ${steps.size} done", 13f, p.muted, bold = false))
            addView(Ui.bar(this@MainActivity, doneCount.toFloat() / steps.size, p.accent, p.track), Ui.lp(this@MainActivity, 10))
        }
        card.addView(head)
        steps.forEach { s ->
            card.addView(Ui.divider(this, 0))
            card.addView(if (s.done)
                Ui.row(this, R.drawable.ui_check, "green", s.title, sub = s.sub, chevron = false)
            else Ui.row(this, s.icon, s.tone, s.title, sub = s.sub) { s.fix(this) })
        }
        return card
    }

    // ----- stats -----

    private fun statsRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(
            Triple(R.drawable.ui_mic, "blue", Stats.todayWords(this).toString() to "words today"),
            Triple(R.drawable.ui_clock, "green", "${Stats.savedMinutes(this)} min" to "time saved"),
            Triple(R.drawable.ui_spark, "orange", Stats.streak(this).toString() to "day streak"),
        )
        items.forEachIndexed { i, (res, tone, v) ->
            val c = Ui.card(this).apply {
                setPaddingRelative(dp(12), dp(12), dp(12), dp(12))
                addView(Ui.tile(this@MainActivity, res, tone, 30, 17))
                addView(Ui.text(this@MainActivity, v.first, 20f, p.ink).apply { setPaddingRelative(0, dp(8), 0, 0); maxLines = 1 })
                addView(Ui.text(this@MainActivity, v.second, 12f, p.muted, bold = false))
            }
            row.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (i > 0) marginStart = dp(10) })
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        val total = Stats.total(this)
        if (total > 0) wrap.addView(Ui.note(this, "$total dictations · ${Stats.words(this)} words in total").apply {
            setPaddingRelative(dp(4), dp(8), 0, 0)
        })
        return wrap
    }

    // ----- try it -----

    private fun tryCard(): View = Ui.paddedCard(this).apply {
        addView(Ui.note(this@MainActivity, "Tap the box, then tap the bubble and talk. Your dictionary, shortcuts and the \"Other\" style apply here."))
        addView(Ui.field(this@MainActivity, "Tap here to try…", multiLine = true).apply { minLines = 3 }, Ui.lp(this@MainActivity, 10))
    }

    // ----- recent -----

    private fun recent(): View {
        val items = Stats.history(this).take(15)
        if (items.isEmpty()) {
            return Ui.paddedCard(this).apply {
                addView(Ui.note(this@MainActivity, if (Stats.historyOn(this@MainActivity))
                    "What you dictate shows up here, so you can copy it again later. It never leaves your phone."
                else "History is off (Settings → Privacy)."))
            }
        }
        val card = Ui.card(this)
        items.forEachIndexed { i, e ->
            if (i > 0) card.addView(Ui.divider(this, 16))
            val cat = Styles.categoryOf(e.app)
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(16), dp(12), dp(16), dp(12))
                addView(Ui.text(this@MainActivity, e.text, 15f, p.ink, bold = false).apply {
                    maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(Ui.text(this@MainActivity, "${Stats.ago(e.time)} · ${cat.title}", 12f, p.muted, bold = false).apply {
                    setPaddingRelative(0, dp(4), 0, 0)
                })
                isClickable = true
                Ui.ripple(this@MainActivity, this)
                setOnClickListener { copy(e.text); toast("Copied") }
                setOnLongClickListener {
                    Ui.dialog(this@MainActivity).setMessage("Delete this from your history?")
                        .setPositiveButton("Delete") { _, _ -> Stats.deleteEntry(this@MainActivity, e); render() }
                        .setNegativeButton("Cancel", null).show()
                    true
                }
            })
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(card)
        wrap.addView(Ui.note(this, "Tap to copy · hold to delete").apply { setPaddingRelative(dp(4), dp(8), 0, 0) })
        return wrap
    }

    // ----- update -----

    private fun updateCard(r: Updates.Release): View = Ui.card(this).apply {
        background = Ui.rounded(p.accentSoft, dp(20).toFloat())
        addView(Ui.row(this@MainActivity, R.drawable.ui_download, "blue", "New version ready",
            sub = "Build ${r.build} · installs over this one, nothing is lost") { openUpdate(r) })
    }

    private fun openUpdate(r: Updates.Release) {
        // The browser downloads it; Android offers "Update" because it's the same app and signing key.
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))) } }
        toast("Open the download, then tap Update")
    }
}
