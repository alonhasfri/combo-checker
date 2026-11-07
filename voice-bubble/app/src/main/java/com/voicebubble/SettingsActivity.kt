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
            Ui.row(this, R.drawable.ui_star, "gold", "Bubble skin", value = Skins.byId(prefs.skin).title) {
                val cur = Skins.all.indexOfFirst { it.id == prefs.skin }.coerceAtLeast(0)
                Ui.dialog(this).setTitle("Bubble skin").setSingleChoiceItems(Skins.all.map { it.title }.toTypedArray(), cur) { d, i ->
                    prefs.skin = Skins.all[i].id; d.dismiss(); refreshBubble(); render()
                }.setNegativeButton("Cancel", null).show()
            },
            Ui.row(this, R.drawable.ui_circle, "purple", "Bubble size", value = "${prefs.bubbleSizeDp} dp",
                sub = "Or pinch the bubble") {
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

        // AI (optional)
        page.addView(Ui.sectionTitle(this, "AI (optional)"))
        page.addView(Ui.group(this, listOf(
            Ui.row(this, R.drawable.ui_key, "purple", "AI key",
                value = if (Ai.hasKey(this)) Ai.provider(this) + " ✓" else "Not set") { aiKeyDialog() },
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
}
