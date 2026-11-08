package com.voicebubble

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

/** Pick the speech model and download it (once). */
class ModelActivity : Screen() {

    companion object {
        // Download state survives leaving and reopening the screen.
        @Volatile private var downloadingId: String? = null
        @Volatile private var progress = 0f
        @Volatile private var lastError: String? = null
    }

    private var progressText: TextView? = null
    private var progressBarHolder: LinearLayout? = null

    override fun render() {
        val prefs = Prefs(this)
        val page = Ui.page(this)
        page.addView(Ui.backHeader(this, "Speech model"))
        page.addView(Ui.note(this, "The model turns your voice into text, right on your phone. It's downloaded once; after that it works offline. " +
                "If you speak English, pick an English model: they're more accurate and never mistake your language.")
            .apply { setPaddingRelative(dp(4), dp(14), dp(4), 0) })

        page.addView(Ui.sectionTitle(this, "Whisper (all languages, translation)"))
        Models.shown().forEach { m ->
            val chosen = prefs.engine == "whisper" && prefs.modelId == m.id
            val have = Models.isDownloaded(this, m)
            val card = Ui.paddedCard(this)
            (card.background as GradientDrawable).setStroke(dp(if (chosen) 2 else 1), if (chosen) p.accent else p.border)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val accurate = m.id.startsWith("small")
            head.addView(when {
                m.id.startsWith("turbo") -> Ui.tile(this, R.drawable.ui_spark, "red")
                accurate -> Ui.tile(this, R.drawable.ui_target, "purple")
                else -> Ui.tile(this, R.drawable.ui_zap, "orange")
            })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(12), 0, dp(8), 0)
                addView(Ui.text(this@ModelActivity, m.title, 16f, p.ink))
                addView(Ui.text(this@ModelActivity, m.detail, 13f, p.muted, bold = false))
            }
            head.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            head.addView(RadioButton(this).apply { isChecked = chosen; isClickable = false })
            card.addView(head)
            card.addView(Ui.text(this, if (have) "✓ Downloaded" else "Not downloaded · ${m.bytes / 1_000_000} MB", 13f,
                if (have) p.good else p.muted).apply { setPaddingRelative(dp(46), dp(6), 0, 0) })

            if (chosen && !have) {
                if (downloadingId == m.id) {
                    val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    progressBarHolder = holder
                    holder.addView(Ui.bar(this, progress, p.accent, p.track))
                    card.addView(holder, Ui.lp(this, 12))
                    progressText = Ui.note(this, progressLabel(m))
                    card.addView(progressText, Ui.lp(this, 6))
                    card.addView(Ui.secondary(this, "Cancel", R.drawable.ui_x) { Downloader.cancelled = true }, Ui.lp(this, 12))
                } else {
                    lastError?.let { card.addView(Ui.text(this, it, 13f, p.danger, bold = false), Ui.lp(this, 8)) }
                    card.addView(Ui.primary(this, "Download (${m.bytes / 1_000_000} MB)", R.drawable.ui_download) { download(m) }, Ui.lp(this, 12))
                    card.addView(Ui.note(this, "Wi-Fi recommended."), Ui.lp(this, 6))
                }
            }
            card.isClickable = true
            Ui.ripple(this, card)
            card.setOnClickListener {
                if (downloadingId == null && !chosen) {
                    prefs.modelId = m.id
                    prefs.engine = "whisper"
                    refreshBubble()
                    lastError = null
                    render()
                }
            }
            page.addView(card, Ui.lp(this, 14))
        }

        // The Live engine: streaming, text appears while you talk.
        page.addView(Ui.sectionTitle(this, "Live engine (types as you talk)"))
        page.addView(Ui.note(this, "Turns speech into text while you're still talking, so it's ready the moment you stop. " +
                "English only: for other languages or translating, it uses your Whisper model.")
            .apply { setPaddingRelative(dp(4), 0, dp(4), dp(4)) })
        // Did it work the last time you dictated?
        if (prefs.engine == "live" && prefs.liveStatus.isNotEmpty()) {
            val ok = prefs.liveStatus == "ok"
            page.addView(Ui.text(this, if (ok) "Live engine: working ✓" else "Live engine couldn't start: ${prefs.liveStatus}",
                14f, if (ok) p.good else p.danger).apply { setPaddingRelative(dp(4), dp(4), dp(4), dp(4)) })
        }
        Live.models.forEach { lm ->
            val chosen = prefs.engine == "live" && prefs.liveModelId == lm.id
            val have = Live.isDownloaded(this, lm)
            val card = Ui.paddedCard(this)
            (card.background as GradientDrawable).setStroke(dp(if (chosen) 2 else 1), if (chosen) p.accent else p.border)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(Ui.tile(this, R.drawable.ui_zap, if (lm.id == "kroko") "green" else "red"))
            head.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(12), 0, dp(8), 0)
                addView(Ui.text(this@ModelActivity, lm.title, 16f, p.ink))
                addView(Ui.text(this@ModelActivity, lm.detail, 13f, p.muted, bold = false))
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            head.addView(RadioButton(this).apply { isChecked = chosen; isClickable = false })
            card.addView(head)
            card.addView(Ui.text(this, if (have) "✓ Downloaded" else "Not downloaded · ${lm.bytes / 1_000_000} MB", 13f,
                if (have) p.good else p.muted).apply { setPaddingRelative(dp(46), dp(6), 0, 0) })
            val key = "live:${lm.id}"
            if (chosen && !have) {
                if (downloadingId == key) {
                    val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    progressBarHolder = holder
                    holder.addView(Ui.bar(this, progress, p.accent, p.track))
                    card.addView(holder, Ui.lp(this, 12))
                    progressText = Ui.note(this, "Downloading… ${(progress * 100).toInt()}%")
                    card.addView(progressText, Ui.lp(this, 6))
                    card.addView(Ui.secondary(this, "Cancel", R.drawable.ui_x) { Downloader.cancelled = true }, Ui.lp(this, 12))
                } else {
                    lastError?.let { card.addView(Ui.text(this, it, 13f, p.danger, bold = false), Ui.lp(this, 8)) }
                    card.addView(Ui.primary(this, "Download (${lm.bytes / 1_000_000} MB)", R.drawable.ui_download) { downloadLive(lm) }, Ui.lp(this, 12))
                }
            }
            card.isClickable = true
            Ui.ripple(this, card)
            card.setOnClickListener {
                if (downloadingId == null && !chosen) {
                    prefs.liveModelId = lm.id
                    prefs.engine = "live"
                    lastError = null
                    refreshBubble()
                    render()
                }
            }
            page.addView(card, Ui.lp(this, 14))
        }

        // Free up space from a model you no longer use.
        val unused = Models.all.filter { it.id != prefs.modelId && Models.isDownloaded(this, it) }
        val unusedLive = Live.models.filter { !(prefs.engine == "live" && it.id == prefs.liveModelId) && Live.isDownloaded(this, it) }
        if (unused.isNotEmpty() || unusedLive.isNotEmpty()) {
            page.addView(Ui.sectionTitle(this, "Storage"))
            page.addView(Ui.group(this, unused.map { m ->
                Ui.row(this, R.drawable.ui_trash, "red", "Delete ${m.title.substringBefore(" (")}", value = "${m.bytes / 1_000_000} MB",
                    chevron = false, danger = true) {
                    Models.file(this, m).delete()
                    Whisper.release()
                    render()
                }
            } + unusedLive.map { lm ->
                Ui.row(this, R.drawable.ui_trash, "red", "Delete ${lm.title.substringBefore(" (")}", value = "${lm.bytes / 1_000_000} MB",
                    chevron = false, danger = true) {
                    Live.release()
                    Live.dir(this, lm).deleteRecursively()
                    render()
                }
            }))
        }
        Ui.plainScreen(this, page)
    }

    /** Lets a running bubble pick up the new engine/model. */
    private fun refreshBubble() {
        if (BubbleService.running) startForegroundService(android.content.Intent(this, BubbleService::class.java))
    }

    /** Downloads a Live model's files one after another, with one progress bar for all of them. */
    private fun downloadLive(lm: Live.LiveModel) {
        if (downloadingId != null) return
        downloadingId = "live:${lm.id}"
        progress = 0f
        lastError = null
        render()
        val app = applicationContext
        Thread {
            var err: String? = null
            var doneBytes = 0L
            for ((name, size) in lm.files) {
                val target = Live.file(app, lm, name)
                if (target.exists() && target.length() == size) { doneBytes += size; continue }
                val before = doneBytes
                err = Downloader.download(lm.url(name), target, size) { f ->
                    progress = (before + f * size) / lm.bytes
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        progressText?.text = "Downloading… ${(progress * 100).toInt()}% (${(progress * lm.bytes / 1_000_000).toInt()} of ${lm.bytes / 1_000_000} MB)"
                        progressBarHolder?.let { h -> h.removeAllViews(); h.addView(Ui.bar(this, progress, p.accent, p.track)) }
                    }
                }
                if (err != null) break
                doneBytes += size
            }
            downloadingId = null
            lastError = if (err == null || err == "Cancelled") null else err
            if (err == null) refreshBubble()
            runOnUiThread { if (!isFinishing) render() }
        }.start()
    }

    private fun progressLabel(m: Model) =
        "Downloading… ${(progress * 100).toInt()}% (${(progress * m.bytes / 1_000_000).toInt()} of ${m.bytes / 1_000_000} MB)"

    private fun download(m: Model) {
        if (downloadingId != null) return
        downloadingId = m.id
        progress = 0f
        lastError = null
        render()
        val app = applicationContext
        Thread {
            val err = Downloader.download(m.url, Models.file(app, m), m.bytes) { f ->
                progress = f
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    progressText?.text = progressLabel(m)
                    progressBarHolder?.let { h -> h.removeAllViews(); h.addView(Ui.bar(this, f, p.accent, p.track)) }
                }
            }
            downloadingId = null
            lastError = if (err == null || err == "Cancelled") null else err
            runOnUiThread { if (!isFinishing) render() }
        }.start()
    }
}
