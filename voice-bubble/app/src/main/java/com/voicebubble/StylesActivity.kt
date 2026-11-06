package com.voicebubble

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

/** Pick a writing style for each kind of app, with live examples. */
class StylesActivity : BaseActivity() {

    private var cat = Styles.Category.PERSONAL
    private lateinit var tabs: LinearLayout
    private lateinit var icons: LinearLayout
    private lateinit var where: TextView
    private lateinit var options: LinearLayout
    private lateinit var access: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page { col ->
            col.addView(text("Styles", 24f, bold = true))
            col.addView(text("How your words are written, depending on the app you're in.", 14f, color = grey)
                .apply { setPadding(0, dp(4), 0, dp(4)) })

            access = card(col, "")
            tabs = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, dp(4))
            }
            col.addView(tabs)
            icons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            col.addView(icons)
            where = text("", 14f, color = grey).apply { setPadding(0, dp(6), 0, 0) }
            col.addView(where)
            options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(options)
            col.addView(text("Styles are made for English. In other languages only the caps and punctuation change.",
                12f, color = grey).apply { setPadding(0, dp(16), 0, 0); gravity = Gravity.CENTER })
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        // Permission card: without it every app counts as "Other".
        access.removeAllViews()
        if (AppDetector.canDetect(this)) {
            access.visibility = View.GONE
        } else {
            access.visibility = View.VISIBLE
            access.addView(text("Let it see which app you're in", 16f, bold = true))
            access.addView(text("Without this, every app uses the \"Other\" style. Tap below, find Voice Bubble and allow it. " +
                    "If it's greyed out: Settings → Apps → Voice Bubble → ⋮ → Allow restricted settings, then try again.",
                13f, color = grey))
            access.addView(button("Allow usage access") {
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))) }
                    .onFailure { runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
            })
        }

        tabs.removeAllViews()
        Styles.Category.values().forEach { c ->
            val on = c == cat
            tabs.addView(text(c.title, 14f, bold = on, color = if (on) Color.WHITE else Color.rgb(40, 44, 56)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = 10 * d
                    setColor(if (on) Color.rgb(36, 110, 220) else Color.WHITE)
                }
                setOnClickListener { cat = c; render() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }

        icons.removeAllViews()
        val installed = (Styles.apps[cat] ?: emptyList()).mapNotNull { p ->
            runCatching { packageManager.getApplicationIcon(p) }.getOrNull()
        }
        installed.take(8).forEach { ic ->
            icons.addView(ImageView(this).apply { setImageDrawable(ic) },
                LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(8) })
        }
        icons.visibility = if (installed.isEmpty()) View.GONE else View.VISIBLE
        where.text = cat.where

        options.removeAllViews()
        val chosen = Styles.get(this, cat)
        val sample = Styles.samples.getValue(cat)
        Styles.Style.values().forEach { s ->
            val c = card(options, "")
            val sel = s == chosen
            (c.background as GradientDrawable).setStroke(if (sel) dp(2) else 0, Color.rgb(36, 110, 220))
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titles.addView(text(s.title, 18f, bold = true))
            titles.addView(text(s.subtitle, 14f, color = grey))
            head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(RadioButton(this).apply { isChecked = sel; isClickable = false })
            c.addView(head)
            c.addView(text(Styles.apply(sample, s, cat), 15f).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(Color.rgb(243, 244, 248)) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            c.setOnClickListener { Styles.set(this, cat, s); render() }
        }
    }
}
