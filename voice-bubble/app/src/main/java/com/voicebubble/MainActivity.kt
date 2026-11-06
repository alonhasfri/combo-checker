package com.voicebubble

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView

/** Setup screen: pick and download the speech model, grant the permissions, start the bubble. */
class MainActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var modelStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadBtn: Button
    private lateinit var micBtn: Button
    private lateinit var overlayBtn: Button
    private lateinit var typeBtn: Button
    private lateinit var startBtn: Button
    private lateinit var updateCard: LinearLayout
    private lateinit var updateText: TextView
    private lateinit var versionText: TextView
    private lateinit var wordsInfo: TextView
    private var update: Updates.Release? = null
    @Volatile private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        page { col -> build(col) }
    }

    private fun build(col: LinearLayout) {
        col.addView(text("Voice Bubble", 26f, bold = true))
        col.addView(text("A floating microphone for every app. Tap it, talk, tap again: your words are typed in. " +
                "Everything runs on your phone, for free.", 15f, color = Color.rgb(90, 96, 110)).apply { setPadding(0, dp(6), 0, dp(8)) })

        // Update banner (hidden until a newer build is found).
        updateCard = card(col, "")
        updateCard.visibility = View.GONE
        updateText = text("", 15f, bold = true)
        updateCard.addView(updateText)
        updateCard.addView(text("Tap Update, open the download, then tap Update again. Your model, words and settings stay.",
            13f, color = grey))
        updateCard.addView(button("Update") { openUpdate() })

        // 1. Model
        val c1 = card(col, "1. Speech model")
        val group = RadioGroup(this)
        Models.all.forEachIndexed { i, m ->
            group.addView(RadioButton(this).apply {
                id = 1000 + i
                text = "${m.title}\n${m.detail}"
                textSize = 14f
                isChecked = prefs.modelId == m.id
                setPadding(dp(4), dp(6), 0, dp(6))
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            prefs.modelId = Models.all[id - 1000].id
            refresh()
        }
        c1.addView(group)
        modelStatus = text("", 14f).apply { setPadding(0, dp(6), 0, dp(4)) }
        c1.addView(modelStatus)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; visibility = View.GONE }
        c1.addView(progress)
        downloadBtn = button("Download") { download() }
        c1.addView(downloadBtn)

        // 2. Language
        val c2 = card(col, "2. Language you speak")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Langs.all.map { it.second })
        spinner.setSelection(Langs.all.indexOfFirst { it.first == prefs.lang }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.lang = Langs.all[pos].first }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        c2.addView(spinner)
        c2.addView(text("Picking your language is a bit more accurate than auto-detect.", 13f, color = grey))

        // 3. Permissions
        val c3 = card(col, "3. Permissions")
        micBtn = button("") {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(perms.toTypedArray(), 1)
        }
        overlayBtn = button("") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        typeBtn = button("") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        c3.addView(micBtn)
        c3.addView(overlayBtn)
        if (BuildConfig.AUTOTYPE) {
            c3.addView(typeBtn)
            c3.addView(text("\"Type into apps\" is optional. Without it, the text is copied and you paste it. " +
                    "To turn it on: Installed apps → Voice Bubble → On. If Android says it's a restricted setting: " +
                    "App info → ⋮ → Allow restricted settings, then try again. It is only used to put your words into the selected text box.",
                13f, color = grey))
        } else {
            c3.addView(text("Your words are copied when you finish talking. Tap the copied text in your keyboard's " +
                    "suggestion bar (Gboard shows it there) or long-press the text box and tap Paste.",
                13f, color = grey))
        }

        // 4. Start
        val c4 = card(col, "4. Floating microphone")
        startBtn = button("") { toggleBubble() }.apply {
            textSize = 17f
            minHeight = dp(56)
        }
        c4.addView(startBtn)
        c4.addView(android.widget.CheckBox(this).apply {
            text = "Only show while a keyboard is open"
            textSize = 15f
            isChecked = prefs.onlyWithKeyboard
            setPadding(dp(4), dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                prefs.onlyWithKeyboard = on
                // Let a running bubble pick up the change now.
                if (BubbleService.running) startForegroundService(Intent(this@MainActivity, BubbleService::class.java))
            }
        })
        c4.addView(text("Tap: start listening · Tap again: stop and type it in · Long-press: cancel · Drag: move it. " +
                "It moves up by itself when the keyboard would cover it. If it doesn't appear while you type, untick the box above.",
            13f, color = grey))

        // Dictionary & shortcuts
        val cw = card(col, "Dictionary & shortcuts")
        wordsInfo = text("", 14f, color = grey)
        cw.addView(wordsInfo)
        cw.addView(button("Open dictionary & shortcuts") { startActivity(Intent(this, WordsActivity::class.java)) })

        // Try it
        val c5 = card(col, "Try it here")
        c5.addView(EditText(this).apply {
            hint = "Tap here, then tap the bubble and talk"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP
        })

        // Version and manual update check
        versionText = text("", 13f, color = grey).apply { setPadding(dp(4), dp(18), 0, 0) }
        col.addView(versionText)
        col.addView(button("Check for updates") { checkUpdates(force = true) })
    }

    private fun checkUpdates(force: Boolean) {
        if (force) versionText.text = "Checking…"
        Updates.check(this, force) { r ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                update = r
                updateCard.visibility = if (r != null) View.VISIBLE else View.GONE
                if (r != null) updateText.text = "⬇ A new version is ready (build ${r.build})"
                versionText.text = "Version: build ${Updates.current}" + when {
                    !force -> ""
                    r != null -> " · build ${r.build} is available (see top)"
                    else -> " · this is the newest (or no connection)"
                }
            }
        }
    }

    private fun openUpdate() {
        val r = update ?: return
        // The browser downloads it; Android offers "Update" because it's the same app and signing key.
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))) } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        checkUpdates(force = false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val m = Models.byId(prefs.modelId)
        val have = Models.isDownloaded(this, m)
        if (!downloading) {
            modelStatus.text = if (have) "✓ Downloaded. Works offline." else "Not downloaded yet (${m.bytes / 1_000_000} MB, one time; Wi-Fi recommended)"
            downloadBtn.visibility = if (have) View.GONE else View.VISIBLE
            downloadBtn.text = "Download"
            progress.visibility = View.GONE
        }
        micBtn.text = if (hasMic()) "✓ Microphone" else "Allow microphone"
        micBtn.isEnabled = !hasMic()
        overlayBtn.text = if (Settings.canDrawOverlays(this)) "✓ Show over other apps" else "Allow showing over other apps"
        overlayBtn.isEnabled = !Settings.canDrawOverlays(this)
        typeBtn.text = if (InsertService.isOn) "✓ Type into apps" else "Turn on typing into apps (optional)"
        val nw = Words.dictionary(this).size
        val ns = Words.shortcuts(this).size
        wordsInfo.text = if (nw + ns == 0) "Teach it names and words it gets wrong, and add shortcuts like \"btw\" → \"by the way\"."
            else "$nw words · $ns shortcuts"
        startBtn.text = if (BubbleService.running) "Hide floating microphone" else "Show floating microphone"
    }

    private fun download() {
        if (downloading) {
            Downloader.cancelled = true
            return
        }
        val m = Models.byId(prefs.modelId)
        downloading = true
        downloadBtn.text = "Cancel"
        progress.visibility = View.VISIBLE
        progress.progress = 0
        modelStatus.text = "Downloading…"
        Thread {
            val err = Downloader.download(m.url, Models.file(this, m), m.bytes) { f ->
                runOnUiThread {
                    progress.progress = (f * 1000).toInt()
                    modelStatus.text = "Downloading… ${(f * 100).toInt()}%  (${(f * m.bytes / 1_000_000).toInt()} of ${m.bytes / 1_000_000} MB)"
                }
            }
            runOnUiThread {
                downloading = false
                refresh()
                if (err != null) modelStatus.text = err
            }
        }.start()
    }

    private fun toggleBubble() {
        if (BubbleService.running) {
            startService(Intent(this, BubbleService::class.java).setAction(BubbleService.ACTION_STOP))
            startBtn.postDelayed({ refresh() }, 300)
            return
        }
        val missing = when {
            !Models.isDownloaded(this, Models.byId(prefs.modelId)) -> "Download the speech model first (step 1)."
            !hasMic() -> "Allow the microphone first (step 3)."
            !Settings.canDrawOverlays(this) -> "Allow showing over other apps first (step 3)."
            else -> null
        }
        if (missing != null) {
            android.widget.Toast.makeText(this, missing, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, BubbleService::class.java))
        startBtn.postDelayed({ refresh() }, 300)
    }
}
