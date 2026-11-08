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
/** Choose a length limit (in characters). [save] gets 0 for "no limit"; [pkg] = for one app. */
fun pickLimit(a: android.app.Activity, pkg: String? = null, done: () -> Unit) {
    val opts = listOf(0 to "No limit", 100 to "100 characters", 150 to "150 characters", 200 to "200 characters (Roblox)",
        280 to "280 characters (X / Twitter)", 500 to "500 characters", -1 to "Custom…")
    fun save(n: Int) {
        if (pkg == null) Prefs(a).maxChars = n else Styles.setAppLimit(a, pkg, n)
        done()
    }
    Ui.dialog(a).setTitle("Longest message").setItems(opts.map { it.second }.toTypedArray()) { _, i ->
        val n = opts[i].first
        if (n >= 0) save(n) else {
            val f = Ui.field(a, "Characters, e.g. 250").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
            Ui.dialog(a).setTitle("Custom limit").setView(Ui.padded(a, f))
                .setPositiveButton("Save") { _, _ -> save(f.text.toString().toIntOrNull()?.coerceIn(20, 5000) ?: 0) }
                .setNegativeButton("Cancel", null).show()
        }
    }.setNegativeButton("Cancel", null).show()
}

class MainActivity : Screen() {

    private var update: Updates.Release? = null
    private var statusLine: TextView? = null
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { verifyKeyboard(); updateStatusLine(); ticker.postDelayed(this, 700) }
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

        page.addView(Ui.sectionTitle(this, "Quick settings"))
        page.addView(quickSettings())

        page.addView(Ui.sectionTitle(this, "Writing helpers"))
        page.addView(helpers())
        page.addView(commandsCard(), Ui.lp(this, 12))

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
        card.addView(Ui.text(this, "Tap to talk · tap again to finish · tap while it writes to cancel · hold to cancel · drag to move",
            12f, 0xBFFFFFFF.toInt(), bold = false).apply { setPaddingRelative(0, dp(10), 0, 0); gravity = Gravity.CENTER })
        return card
    }

    private fun updateStatusLine() {
        val v = statusLine ?: return
        val prefs = Prefs(this)
        v.text = when {
            !BubbleService.running -> if (Setup.readyToStart(this)) "Tap the button to show the bubble" else "A few steps left (see below)"
            !prefs.onlyWithKeyboard -> "Always on screen"
            InsertService.isOn || (prefs.keyboardVerified && !prefs.keyboardBroken) ->
                if (BubbleService.keyboardNow) "Keyboard open · bubble showing" else "Hidden until a keyboard opens"
            prefs.keyboardBroken -> "Always on screen (keyboard detection doesn't work on this phone)"
            else -> "On screen · tap the Try it box once to finish setting up \"only while typing\""
        }
    }

    // ----- keyboard check -----
    private var ownKeyboardTicks = 0

    /**
     * Checks the bubble's keyboard detection against this screen's own keyboard, which Android
     * reports reliably. Agrees → hiding is allowed. Keyboard clearly open but not detected → the
     * bubble stays on screen for good, so it can never get stuck hidden.
     */
    private fun verifyKeyboard() {
        if (android.os.Build.VERSION.SDK_INT < 30 || !BubbleService.running) return
        val ownOpen = window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
        if (!ownOpen) { ownKeyboardTicks = 0; return }
        ownKeyboardTicks++
        val prefs = Prefs(this)
        if (BubbleService.keyboardNow) {
            if (!prefs.keyboardVerified || prefs.keyboardBroken) {
                prefs.keyboardVerified = true; prefs.keyboardBroken = false
                toast("Keyboard detection works ✓ The bubble will hide when you're not typing")
            }
        } else if (ownKeyboardTicks >= 4 && !prefs.keyboardBroken) {
            prefs.keyboardBroken = true; prefs.keyboardVerified = false
            toast("Keyboard detection doesn't work on this phone, so the bubble will stay on screen")
        }
    }

    // ----- quick settings (the important ones; the rest is under the gear) -----

    private fun quickSettings(): View {
        val prefs = Prefs(this)
        val model = Models.byId(prefs.modelId)
        val live = prefs.engine == "live"
        val rows = mutableListOf<View>(
            Ui.row(this, R.drawable.ui_cpu, "purple", "Speech model",
                value = (if (live) Live.byId(prefs.liveModelId).title else model.title).substringBefore(" (") +
                    if (Setup.modelReady(this)) "" else " · download",
                valueColor = if (Setup.modelReady(this)) null else p.danger) {
                startActivity(Intent(this, ModelActivity::class.java))
            },
        )
        if (!model.englishOnly) rows += Ui.row(this, R.drawable.ui_globe, "teal", "Language",
            value = Langs.all.firstOrNull { it.first == prefs.lang }?.second) {
            val names = Langs.all.map { it.second }.toTypedArray()
            val cur = Langs.all.indexOfFirst { it.first == prefs.lang }.coerceAtLeast(0)
            Ui.dialog(this).setTitle("Language you speak").setSingleChoiceItems(names, cur) { d, i ->
                prefs.lang = Langs.all[i].first; d.dismiss(); render()
            }.setNegativeButton("Cancel", null).show()
        }
        rows += Ui.switchRow(this, R.drawable.ui_layout, "blue", "Only while typing",
            "Show the bubble only when a keyboard is open", prefs.onlyWithKeyboard) { on ->
            prefs.onlyWithKeyboard = on; refreshBubble(); updateStatusLine()
        }
        rows += Ui.switchRow(this, R.drawable.ui_upload, "teal", "Sit right above the keyboard",
            "Drag it to set how high", prefs.dockAboveKeyboard) { on ->
            prefs.dockAboveKeyboard = on; prefs.dockLift = 0; refreshBubble()
        }
        rows += Ui.switchRow(this, R.drawable.ui_type, "orange", "Caps lock",
            "EVERYTHING IN CAPITALS. Also the ABC button next to the bubble while you speak", prefs.capsLock) { on ->
            prefs.capsLock = on; refreshBubble()
        }
        rows += Ui.row(this, R.drawable.ui_file, "orange", "Length limit",
            value = if (prefs.maxChars > 0) "${prefs.maxChars} characters" else "Off",
            sub = "Longest message. Roblox gets 200 automatically") { pickLimit(this) { render() } }
        if (prefs.engine == "live" && BuildConfig.AUTOTYPE) rows += Ui.switchRow(this, R.drawable.ui_wand, "gold", "Type while I talk",
            "Live engine: words appear in the text box as you speak", prefs.typeWhileTalking) { on -> prefs.typeWhileTalking = on }
        rows += Ui.switchRow(this, R.drawable.ui_book, "green", "Learn how I talk",
            "Remembers your fixes, names you use and how you write", Adapt.isOn(this)) { on -> Adapt.setOn(this, on) }
        rows += Ui.switchRow(this, R.drawable.ui_type, "blue", "Live text",
            "Shows your words next to the bubble as you talk", prefs.liveText) { on -> prefs.liveText = on }
        rows += Ui.switchRow(this, R.drawable.ui_target, "green", "Extra accuracy",
            "Slower, more careful", prefs.extraAccuracy) { on -> prefs.extraAccuracy = on }
        return Ui.group(this, rows)
    }

    /** The switchable text helpers, plus translation. Each can also be switched by voice. */
    private fun helpers(): View {
        val prefs = Prefs(this)
        val rows = mutableListOf<View>()
        listOf(
            Triple(Commands.Feature.COMMANDS, R.drawable.ui_mic to "blue", "\"new line\", \"question mark\", \"scratch that\""),
            Triple(Commands.Feature.EMOJI, R.drawable.ui_spark to "gold", "\"fire emoji\" → 🔥, \"skull emoji\" → 💀"),
            Triple(Commands.Feature.FILLER, R.drawable.ui_x to "red", "Removes um, uh and repeated words"),
            Triple(Commands.Feature.NUMBERS, R.drawable.ui_clock to "green", "\"twenty five percent\" → 25%, \"March third\" → March 3"),
        ).forEach { (f, look, sub) ->
            rows += Ui.switchRow(this, look.first, look.second, f.title, sub, Features.isOn(this, f)) { on -> Features.set(this, f, on) }
        }
        rows += Ui.row(this, R.drawable.ui_user, "orange", "Talk like", value = prefs.persona ?: "Off",
            sub = if (prefs.persona != null && !Ai.hasKey(this)) "Needs an AI key (Settings → AI)" else "Rewrite everything you say in someone's voice") {
            pickPersona()
        }
        val langs = listOf("Off", "English", "Hebrew", "Spanish", "French", "German", "Italian", "Portuguese", "Arabic",
            "Russian", "Japanese", "Korean", "Chinese")
        rows += Ui.row(this, R.drawable.ui_globe, "purple", "Translate into", value = prefs.translateTo ?: "Off",
            sub = if (prefs.translateTo.equals("English", true)) "Into English happens on the phone"
                  else if (prefs.translateTo != null && !Ai.hasKey(this)) "Needs an AI key (Settings → AI)" else null) {
            val cur = langs.indexOf(prefs.translateTo ?: "Off").coerceAtLeast(0)
            Ui.dialog(this).setTitle("Translate everything you say into").setSingleChoiceItems(langs.toTypedArray(), cur) { d, i ->
                prefs.translateTo = if (i == 0) null else langs[i]
                d.dismiss(); render()
            }.setNegativeButton("Cancel", null).show()
        }
        return Ui.group(this, rows)
    }

    /** The spoken commands, so you know what to say. */
    private fun commandsCard(): View = Ui.paddedCard(this).apply {
        val w = Prefs(this@MainActivity).commandWord
        val head = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(this@MainActivity, "Command word: \"$w\"", 15f, p.ink), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.button(this@MainActivity, "Change", R.drawable.ui_edit, p.accentSoft, p.accentInk, heightDp = 38) { changeWord() })
        addView(head)
        addView(Ui.note(this@MainActivity,
            "Say it first (or after what you dictated) to give a command:\n" +
            "• \"$w, enable numbers and dates\" / \"…disable filler remover\"\n" +
            "• \"$w, rewrite, make this more polite\" / \"…make this shorter\"\n" +
            "• \"$w, translate to Spanish\" / \"…stop translating\"\n" +
            "• \"$w, reply\" / \"…reply in gen z\" / \"…reply like Gojo, saying I'm late\"\n" +
            "• \"$w, summarise\" (the chat on screen)\n" +
            "• \"$w, make me sound like Shakespeare\" / \"…talk like Rouxls Kaard from now on\" / \"…talk normally\"\n" +
            "• \"$w, offline Shakespeare\" (instant) / \"…AI Shakespeare\" / \"…back to normal\"\n" +
            "• \"$w, undo\", or just \"scratch that\"\n" +
            "Rewrite, reply, summarise and most translations need an AI key (Settings → AI). Reply, summarise and undo need the autotype version."
        ).apply { setPaddingRelative(0, dp(8), 0, 0) })
    }

    private fun changeWord() {
        val f = Ui.field(this, Commands.DEFAULT_WORD).apply { setText(Prefs(this@MainActivity).commandWord) }
        Ui.dialog(this).setTitle("Command word").setMessage("A phrase you wouldn't normally say, e.g. \"hey bubble\" or \"out of context\".")
            .setView(Ui.padded(this, f))
            .setPositiveButton("Save") { _, _ -> Prefs(this).commandWord = f.text.toString().ifBlank { Commands.DEFAULT_WORD }; render() }
            .setNeutralButton("Default") { _, _ -> Prefs(this).commandWord = Commands.DEFAULT_WORD; render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickPersona() {
        val prefs = Prefs(this)
        val presets = listOf(
            // Deltarune & Undertale
            "Rouxls Kaard", "Spamton", "Jevil", "Sans", "Papyrus", "Undyne", "Alphys", "Mettaton", "Flowey",
            "Toriel", "Asgore", "Temmie", "Susie", "Ralsei", "Lancer", "Berdly", "Noelle", "Tenna", "Queen",
            // Jujutsu Kaisen
            "Gojo", "Sukuna", "Yuji Itadori", "Megumi", "Nobara", "Nanami", "Todo", "Mahito", "Kenjaku", "Hakari",
            // Other anime & games
            "Goku", "Vegeta", "Naruto", "Luffy", "Zoro", "All Might", "Light Yagami", "Saitama", "Rimuru",
            "Mario", "Sonic", "Kratos", "GLaDOS", "a Minecraft villager",
            // Famous voices & styles
            "Shakespeare", "Yoda", "Gollum", "Sherlock Holmes", "a pirate", "a medieval knight", "a cowboy",
            "a news reporter", "a sports commentator", "a motivational coach", "a robot", "a villain monologue",
            "a fairy tale narrator", "a Gen Z teenager", "a grumpy old man", "an overly polite butler",
        )
        val items = (listOf("Off") + presets + "Someone else…").toTypedArray()
        Ui.dialog(this).setTitle("Talk like").setItems(items) { _, i ->
            when {
                i == 0 -> { prefs.persona = null; render() }
                i == items.size - 1 -> {
                    val f = Ui.field(this, "e.g. Yoda, a cowboy, my grandma")
                    Ui.dialog(this).setTitle("Talk like…").setView(Ui.padded(this, f))
                        .setPositiveButton("Save") { _, _ -> prefs.persona = f.text.toString(); render() }
                        .setNegativeButton("Cancel", null).show()
                }
                else -> { prefs.persona = presets[i - 1]; render() }
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun refreshBubble() {
        if (BubbleService.running) startForegroundService(Intent(this, BubbleService::class.java))
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
