package com.voicebubble

import android.content.Intent

/** Settings (gear): the less-used options. The important ones are on Home (Quick settings). */
class SettingsActivity : Screen() {

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = render()

    override fun render() {
        val prefs = Prefs(this)
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Settings"))

        // Bubble
        page.addView(Ui.sectionTitle(this, "Floating microphone"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_star, "gold", "Bubble skin", value = Skins.byId(this, prefs.skin).title) { pickSkin() },
            Ui.switchRow(this, R.drawable.ui_spark, "purple", "Animated skins",
                "Themed skins move: rings spin, stars twinkle, the soul beats", prefs.animSkins) { on -> prefs.animSkins = on; refreshBubble() },
            Ui.row(this, R.drawable.ui_zap, "orange", "Quick phrases", value = "${prefs.quickPhrases.size}",
                sub = "Swipe up on the bubble, tap one to type it") { editQuickPhrases() },
            Ui.row(this, R.drawable.ui_circle, "purple", "Bubble size", value = "${prefs.bubbleSizeDp} dp",
                sub = "Bigger or smaller bubble") {
                val sizes = listOf(52 to "Small", 72 to "Medium", 92 to "Large", 112 to "Extra large")
                val cur = sizes.indexOfFirst { it.first >= prefs.bubbleSizeDp }.let { if (it < 0) sizes.size - 1 else it }
                Ui.dialog(this).setTitle("Bubble size").setSingleChoiceItems(sizes.map { it.second }.toTypedArray(), cur) { d, i ->
                    prefs.bubbleSizeDp = sizes[i].first; d.dismiss(); refreshBubble(); render()
                }.setNegativeButton("Cancel", null).show()
            },
            Ui.row(this, R.drawable.ui_bell, "teal", "Sounds", value = mapOf("off" to "Off", "chime" to "Chime", "blip" to "Retro blips")[prefs.sound]) {
                val opts = listOf("off" to "Off", "chime" to "Chime", "blip" to "Retro blips")
                val cur = opts.indexOfFirst { it.first == prefs.sound }.coerceAtLeast(0)
                Ui.dialog(this).setTitle("Start and stop sounds").setSingleChoiceItems(opts.map { it.second }.toTypedArray(), cur) { d, i ->
                    prefs.sound = opts[i].first; Sounds.start(prefs.sound); d.dismiss(); render()
                }.setNegativeButton("Cancel", null).show()
            },
            Ui.switchRow(this, R.drawable.ui_zap, "orange", "Vibrate", "A short buzz when it starts and stops listening", prefs.haptics) { on ->
                prefs.haptics = on
            },
            Ui.row(this, R.drawable.ui_reset, "gray", "Reset bubble position", chevron = false) {
                prefs.bubbleX = -1; prefs.bubbleY = -1; prefs.dockLift = 0
                // Also test keyboard detection again.
                prefs.keyboardVerified = false; prefs.keyboardBroken = false
                if (BubbleService.running) {
                    startService(Intent(this, BubbleService::class.java).setAction(BubbleService.ACTION_STOP))
                    window.decorView.postDelayed({ startForegroundService(Intent(this, BubbleService::class.java)) }, 400)
                }
                toast("Moved back to the right edge. Tap Home's Try it box to re-check the keyboard.")
            },
        )))

        page.addView(Ui.note(this, when {
            BubbleService.drawnOverKeyboard -> "✓ The bubble is drawn on top of the keyboard. You can drag it onto the keys."
            BuildConfig.AUTOTYPE -> "To draw the bubble on top of the keyboard (not just above it), turn on \"Type into apps\" below. " +
                "Android only lets accessibility services draw over the keyboard."
            else -> "Android only lets accessibility services draw over the keyboard, so in this version the bubble sits just above it. " +
                "The autotype version can draw it on top of the keyboard."
        }).apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        page.addView(Ui.note(this, "Hold the bubble for a second to move it (it buzzes) and to see the X that turns it off. " +
                "Pinch it to make it bigger or smaller. Swipe down from the top of the screen to add the Voice Bubble " +
                "Quick Settings tile.").apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        if (BuildConfig.AUTOTYPE) {
            page.addView(Ui.sectionTitle(this, "Learning"))
            page.addView(Ui.group(this, listOf(
                Ui.switchRow(this, R.drawable.ui_book, "green", "Learn from my fixes",
                    "When you correct a word it typed, offer to add it to your dictionary", prefs.learnFixes) { on -> prefs.learnFixes = on },
            )))
        }

        // AI (optional)
        page.addView(Ui.sectionTitle(this, "AI (optional)"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_key, "purple", "AI key",
                value = if (Ai.hasKey(this)) Ai.provider(this) + " ✓" else "Not set") { aiKeyDialog() },
            Ui.switchRow(this, R.drawable.ui_book, "purple", "Instant Royal English",
                "1500s royal talk on the phone with no AI: no waiting, but simpler", prefs.instantRoyal) { on -> prefs.instantRoyal = on },
            Ui.switchRow(this, R.drawable.ui_zap, "gold", "Instant characters",
                "${Instant.supported.joinToString(", ")}: done on the phone with no AI. No waiting, but simpler",
                prefs.instantChars) { on -> prefs.instantChars = on },
            Ui.switchRow(this, R.drawable.ui_target, "green", "Roblox-safe words",
                "In Roblox, swaps words its chat filter hides (swearing, numbers, other apps' names)", prefs.robloxSafe) { on -> prefs.robloxSafe = on },
            Ui.switchRow(this, R.drawable.ui_zap, "orange", "Fast Claude",
                "With a Claude key: quicker answers from Claude Haiku 5.5 instead of Claude Opus 5.5", prefs.aiFast) { on -> prefs.aiFast = on },
        )))
        page.addView(Ui.note(this, "Needed for rewrite, reply suggestions and translating into languages other than English. " +
                "Use the same key as Comboed: a free Google Gemini key (AIza…) or OpenRouter key (sk-or-…). An Anthropic key (sk-ant-…) for Claude works too. " +
                "Only the text being rewritten or translated, or the chat on screen for replies, is sent.")
            .apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        // Permissions
        page.addView(Ui.sectionTitle(this, "Permissions"))
        val perms = mutableListOf(
            permRow(R.drawable.ui_mic, "red", "Microphone", Setup.hasMic(this)) { Setup.askMic(this) },
            permRow(R.drawable.ui_layout, "purple", "Show over other apps", Setup.hasOverlay(this)) { Setup.openOverlay(this) },
            permRow(R.drawable.ui_type, "teal", "See which app you're in", AppDetector.canDetect(this), optional = true) { Setup.openUsage(this) },
        )
        if (BuildConfig.AUTOTYPE) perms += permRow(R.drawable.ui_wand, "gold", "Type into apps", InsertService.isOn, optional = true) {
            Setup.openAccessibility(this)
        }
        page.addView(Ui.group(this, perms))

        // Appearance
        page.addView(Ui.sectionTitle(this, "Appearance"))
        val themes = listOf("system" to "Same as phone", "light" to "Light", "dark" to "Dark")
        page.addView(Ui.group(this, listOf(
            Ui.row(this, if (Ui.isDark(this)) R.drawable.ui_moon else R.drawable.ui_sun, "gold", "Theme",
                value = themes.first { it.first == Ui.themeMode(this) }.second) {
                val cur = themes.indexOfFirst { it.first == Ui.themeMode(this) }
                Ui.dialog(this).setTitle("Theme").setSingleChoiceItems(themes.map { it.second }.toTypedArray(), cur) { d, i ->
                    Ui.setThemeMode(this, themes[i].first); d.dismiss(); render()
                }.setNegativeButton("Cancel", null).show()
            },
        )))

        // Privacy
        page.addView(Ui.sectionTitle(this, "Privacy"))
        page.addView(Ui.group(this, listOf(
            Ui.switchRow(this, R.drawable.ui_clock, "blue", "Keep history", "Recent dictations on Home (last 50)", Stats.historyOn(this)) { on ->
                Stats.setHistoryOn(this, on)
            },
            Ui.row(this, R.drawable.ui_reset, "red", "Forget what it learned", chevron = false, danger = true) {
                Ui.dialog(this).setMessage("Forget remembered fixes, the names it noticed and your recent writing?")
                    .setPositiveButton("Forget") { _, _ -> Adapt.forget(this); toast("Forgotten") }
                    .setNegativeButton("Cancel", null).show()
            },
            Ui.row(this, R.drawable.ui_trash, "red", "Clear history", chevron = false, danger = true) {
                Ui.dialog(this).setMessage("Delete all recent dictations?")
                    .setPositiveButton("Delete") { _, _ -> Stats.clearHistory(this); toast("History cleared") }
                    .setNegativeButton("Cancel", null).show()
            },
        )))
        page.addView(Ui.note(this, "Your voice is turned into text on your phone and never uploaded. The internet is only used " +
                "to download the speech model and to check for updates.").apply { setPaddingRelative(dp(4), dp(8), dp(4), 0) })

        // About
        page.addView(Ui.sectionTitle(this, "About"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_help, "green", "How to use") { help() },
            Ui.row(this, R.drawable.ui_download, "blue", "Check for updates", value = "build ${Updates.current}") { checkUpdates() },
        )))
        page.addView(Ui.note(this, "Voice Bubble ${if (BuildConfig.AUTOTYPE) "(autotype)" else ""} · speech recognition by Whisper (whisper.cpp, MIT licence)")
            .apply { setPaddingRelative(dp(4), dp(10), dp(4), 0) })

        Ui.plainScreen(this, page)
    }

    private fun aiKeyDialog() {
        val f = Ui.field(this, "AIza…, sk-or-… or sk-ant-…").apply {
            setText(Ai.key(this@SettingsActivity))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        Ui.dialog(this).setTitle("AI key").setView(Ui.padded(this, f))
            .setPositiveButton("Save") { _, _ -> Ai.setKey(this, f.text.toString()); render() }
            .setNeutralButton("Get a free key") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun permRow(res: Int, tone: String, label: String, ok: Boolean, optional: Boolean = false, fix: () -> Unit) =
        Ui.row(this, res, tone, label, value = if (ok) "Allowed" else if (optional) "Optional" else "Needed",
            valueColor = if (ok) p.good else if (optional) p.muted else p.danger, chevron = !ok,
            onClick = if (ok) null else fix)

    /** Let a running bubble pick up new settings (size, keyboard mode). */
    private fun refreshBubble() {
        if (BubbleService.running) startForegroundService(Intent(this, BubbleService::class.java))
    }

    private fun checkUpdates() {
        toast("Checking…")
        Updates.check(this, force = true) { r ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r == null) toast("You have the newest version (or no connection)")
                else Ui.dialog(this).setTitle("Build ${r.build} is ready")
                    .setMessage("Tap Download, open the file, then tap Update. Your model, words and settings stay.")
                    .setPositiveButton("Download") { _, _ ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(r.apkUrl))) }
                    }
                    .setNegativeButton("Later", null).show()
            }
        }
    }

    private fun help() {
        Ui.dialog(this).setTitle("How to use").setMessage(
            "• Tap the bubble and talk. Tap it again when you're done: your words are typed in " +
                (if (BuildConfig.AUTOTYPE) "(or copied, if typing into apps is off)" else "(copied: tap them in your keyboard's bar or paste)") + ".\n\n" +
            "• Hold the bubble to cancel. Drag it to move it.\n\n" +
            "• Dictionary: words it should spell your way. Shortcuts: say \"btw\", get \"by the way\".\n\n" +
            "• Style: casual for chats, formal for email, and so on.\n\n" +
            "• Everything runs on your phone. No account, no subscription."
        ).setPositiveButton("Got it", null).show()
    }

    /** All the skins as little bubbles to tap, plus making your own. */
    private fun pickSkin() {
        val prefs = Prefs(this)
        val grid = android.widget.GridLayout(this).apply { columnCount = 4; setPaddingRelative(dp(12), dp(8), dp(12), dp(8)) }
        lateinit var dialog: android.app.AlertDialog
        fun cell(preview: android.view.View, title: String, chosen: Boolean, onLong: (() -> Unit)? = null, onTap: () -> Unit) {
            val v = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPaddingRelative(dp(4), dp(8), dp(4), dp(8))
                addView(preview, android.widget.LinearLayout.LayoutParams(dp(60), dp(60)))
                addView(Ui.text(this@SettingsActivity, title, 12f, if (chosen) p.accent else p.ink, bold = chosen).apply {
                    gravity = android.view.Gravity.CENTER; maxLines = 2
                })
                isClickable = true
                Ui.ripple(this@SettingsActivity, this)
                setOnClickListener { dialog.dismiss(); onTap() }
                onLong?.let { l -> setOnLongClickListener { dialog.dismiss(); l(); true } }
            }
            grid.addView(v, android.widget.GridLayout.LayoutParams().apply {
                width = 0; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            })
        }
        fun actionTile(res: Int, tone: String) = android.widget.FrameLayout(this).apply {
            addView(Ui.tile(this@SettingsActivity, res, tone, 52, 26), android.widget.FrameLayout.LayoutParams(dp(52), dp(52), android.view.Gravity.CENTER))
        }
        // Make your own first.
        cell(actionTile(R.drawable.ui_upload, "blue"), "Upload picture", false) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK_SKIN)
        }
        cell(actionTile(R.drawable.ui_wand, "purple"), "Make with AI", false) { designSkin() }
        cell(actionTile(R.drawable.ui_download, "green"), "Paste a code", false) { pasteSkinCode() }
        Skins.all(this).forEach { sk ->
            val delete: (() -> Unit)? = if (sk.custom) ({
                Ui.dialog(this).setTitle(sk.title).setItems(arrayOf("Share with a friend", "Delete")) { _, which ->
                    if (which == 0) shareSkin(sk.id) else {
                        SkinArt.delete(this, sk.id)
                        if (prefs.skin == sk.id) prefs.skin = "classic"
                        refreshBubble(); render()
                    }
                }.setNegativeButton("Cancel", null).show()
                Unit
            }) else null
            cell(BubbleView(this).apply { skin = sk }, sk.title, sk.id == prefs.skin, delete) {
                prefs.skin = sk.id; refreshBubble(); render()
            }
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(Ui.note(this@SettingsActivity, "Hold one of your own skins to share or delete it.").apply { setPaddingRelative(dp(22), dp(4), dp(22), 0) })
            addView(grid)
        }
        dialog = Ui.dialog(this).setTitle("Bubble skin").setView(android.widget.ScrollView(this).apply { addView(box) })
            .setNegativeButton("Cancel", null).create()
        dialog.show()
    }

    private fun editQuickPhrases() {
        val prefs = Prefs(this)
        val f = Ui.field(this, "One per line, e.g. gg, brb, trade?", multiLine = true).apply { setText(prefs.quickPhrases.joinToString("\n")) }
        Ui.dialog(this).setTitle("Quick phrases").setMessage("One per line. Swipe up on the bubble to use them.")
            .setView(Ui.padded(this, f)).setPositiveButton("Save") { _, _ ->
                prefs.quickPhrases = f.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.take(12)
                render()
            }.setNegativeButton("Cancel", null).show()
    }

    /** Your skin as a code: copied, and offered to share (WhatsApp, Discord…). */
    private fun shareSkin(id: String) {
        val code = SkinArt.shareCode(this, id) ?: run { toast("Couldn't make a code for that skin"); return }
        getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("Voice Bubble skin", code))
        toast("Skin code copied")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            "My Voice Bubble skin! In Voice Bubble: Settings → Bubble skin → Paste a code\n\n$code"), "Share skin"))
    }

    /** A friend's skin code: paste it (or it's taken from what you copied). */
    private fun pasteSkinCode() {
        val f = Ui.field(this, "Paste the skin code (starts with VBSKIN:)", multiLine = true)
        Ui.dialog(this).setTitle("Paste a skin code").setView(Ui.padded(this, f)).setPositiveButton("Add") { _, _ ->
            val text = f.text.toString()
            val code = Regex("VBSKIN:[A-Za-z0-9+/=\\s]+").find(text)?.value ?: text
            val id = runCatching { SkinArt.importCode(this, code) }.getOrNull()
            if (id == null) { toast("That code doesn't work. Copy the whole thing and try again"); return@setPositiveButton }
            Prefs(this).skin = id
            refreshBubble(); render()
            toast("Added \"${Skins.byId(this, id).title}\" and put it on your bubble")
        }.setNegativeButton("Cancel", null).show()
    }

    /** Describe a skin; the AI draws it. */
    private fun designSkin() {
        if (!Ai.hasKey(this)) { toast("Making skins with AI needs an AI key (Settings → AI)"); return }
        val f = Ui.field(this, "e.g. a pizza with sunglasses, a purple galaxy cat, a red soul heart with flames")
        Ui.dialog(this).setTitle("Describe your skin").setView(Ui.padded(this, f)).setPositiveButton("Make it") { _, _ ->
            val what = f.text.toString().trim()
            if (what.isEmpty()) return@setPositiveButton
            toast("Designing your skin…")
            val app = applicationContext
            Thread {
                var spec: org.json.JSONObject? = null
                var problem: String? = null
                // Free AIs sometimes send broken drawings: one more try.
                for (attempt in 0 until 2) {
                    try {
                        spec = SkinArt.parseSpec(Ai.designSkin(app, what))
                        if (spec != null) break
                    } catch (e: Ai.Failure) { problem = e.message; break }
                }
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    val s = spec
                    if (s == null) { toast(problem ?: "The AI's drawing didn't work. Try again or describe it differently"); return@runOnUiThread }
                    val id = SkinArt.addDesign(app, s)
                    Prefs(this).skin = id
                    refreshBubble(); render()
                    toast("Made \"${Skins.byId(this, id).title}\" and put it on your bubble")
                }
            }.start()
        }.setNegativeButton("Cancel", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SKIN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val id = runCatching { SkinArt.addPicture(this, uri) }.getOrNull()
        if (id == null) { toast("Couldn't open that picture"); return }
        Prefs(this).skin = id
        refreshBubble(); render()
        toast("Your picture is your bubble now")
    }

    companion object { private const val PICK_SKIN = 41 }
}
