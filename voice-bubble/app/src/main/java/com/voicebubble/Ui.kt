package com.voicebubble

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * The app's look (same design language as Comboed): light/dark palette, rounded cards, settings
 * rows with coloured icon tiles, segmented tabs and the bottom tab bar
 * (Home · Dictionary · Shortcuts · Style).
 */
object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    class Pal(
        val bg: Int, val card: Int, val border: Int, val ink: Int, val muted: Int,
        val accent: Int, val accentInk: Int, val accentSoft: Int, val track: Int, val divider: Int,
        val danger: Int, val good: Int, val field: Int, val hint: Int,
    )

    private fun c(hex: Long) = hex.toInt()

    // ----- theme -----

    /** "system", "light" or "dark". */
    fun themeMode(ctx: Context): String =
        ctx.getSharedPreferences("voice", Context.MODE_PRIVATE).getString("theme", "system") ?: "system"

    fun setThemeMode(ctx: Context, mode: String) =
        ctx.getSharedPreferences("voice", Context.MODE_PRIVATE).edit().putString("theme", mode).apply()

    fun isDark(ctx: Context): Boolean = when (themeMode(ctx)) {
        "dark" -> true
        "light" -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun pal(ctx: Context): Pal = if (isDark(ctx)) Pal(
        bg = c(0xFF0F1220), card = c(0xFF1A1F33), border = c(0xFF2A3048), ink = c(0xFFEEF1FA),
        muted = c(0xFFA3ABC2), accent = c(0xFF3A5BD9), accentInk = c(0xFFA9BBFF), accentSoft = c(0xFF263266),
        track = c(0xFF2A3048), divider = c(0xFF262C42), danger = c(0xFFFF9DA2), good = c(0xFF7FD9A6),
        field = c(0xFF141829), hint = c(0xFF6E7690),
    ) else Pal(
        bg = c(0xFFF4F6FB), card = c(0xFFFFFFFF), border = c(0xFFE3E7F1), ink = c(0xFF141A2E),
        muted = c(0xFF5B6378), accent = c(0xFF3A5BD9), accentInk = c(0xFF2B47B8), accentSoft = c(0xFFE3E9FF),
        track = c(0xFFE6EAF5), divider = c(0xFFEEF0F6), danger = c(0xFFA8262C), good = c(0xFF17663A),
        field = c(0xFFF4F6FB), hint = c(0xFF8A92A8),
    )

    /** Icon-tile colours (background, icon) for a named tone. */
    fun tone(ctx: Context, name: String): Pair<Int, Int> {
        val dark = isDark(ctx)
        return when (name) {
            "purple" -> if (dark) Pair(c(0xFF34245E), c(0xFFC9B2FF)) else Pair(c(0xFFECE3FF), c(0xFF5A2FC7))
            "orange" -> if (dark) Pair(c(0xFF4A2A1C), c(0xFFFFB08A)) else Pair(c(0xFFFDE6DE), c(0xFFB23A0B))
            "green" -> if (dark) Pair(c(0xFF1D3A2A), c(0xFF7FD9A6)) else Pair(c(0xFFE3F4EA), c(0xFF17663A))
            "teal" -> if (dark) Pair(c(0xFF163646), c(0xFF8FD3F5)) else Pair(c(0xFFDDF0FB), c(0xFF0E6A9E))
            "red" -> if (dark) Pair(c(0xFF4A1E22), c(0xFFFF9DA2)) else Pair(c(0xFFFBE3E4), c(0xFFA8262C))
            "gray" -> if (dark) Pair(c(0xFF2A3048), c(0xFFC3C9DA)) else Pair(c(0xFFEEF0F6), c(0xFF3A4157))
            "gold" -> if (dark) Pair(c(0xFF3F3218), c(0xFFF2C66B)) else Pair(c(0xFFFFF0D1), c(0xFF8A5A00))
            else -> if (dark) Pair(c(0xFF263266), c(0xFFA9BBFF)) else Pair(c(0xFFE3E9FF), c(0xFF2B47B8))
        }
    }

    // ----- building blocks -----

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokePx > 0) setStroke(strokePx, stroke)
    }

    fun icon(ctx: Context, res: Int, color: Int): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(color, PorterDuff.Mode.SRC_IN)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun text(ctx: Context, s: String, sp: Float, color: Int, bold: Boolean = true) = TextView(ctx).apply {
        text = s; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun lp(ctx: Context, topDp: Int = 0, w: Int = MATCH) =
        LinearLayout.LayoutParams(w, WRAP).apply { topMargin = dp(ctx, topDp) }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = pal(ctx)
        background = rounded(p.card, dp(ctx, 20).toFloat(), p.border, dp(ctx, 1))
        clipToOutline = true
    }

    /** A card with inner padding, for free content (text, fields, buttons). */
    fun paddedCard(ctx: Context): LinearLayout = card(ctx).apply {
        setPaddingRelative(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 16))
    }

    fun ripple(ctx: Context, v: View) {
        val tv = TypedValue()
        if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) v.foreground = ctx.getDrawable(tv.resourceId)
    }

    fun page(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 28))
    }

    fun tile(ctx: Context, res: Int, toneName: String, boxDp: Int = 34, iconDp: Int = 19): FrameLayout {
        val (bg, ink) = tone(ctx, toneName)
        return FrameLayout(ctx).apply {
            background = rounded(bg, dp(ctx, 10).toFloat())
            addView(icon(ctx, res, ink), FrameLayout.LayoutParams(dp(ctx, iconDp), dp(ctx, iconDp), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(ctx, boxDp), dp(ctx, boxDp))
        }
    }

    fun sectionTitle(ctx: Context, s: String) = text(ctx, s.uppercase(), 13f, pal(ctx).muted).apply {
        letterSpacing = 0.04f
        setPaddingRelative(dp(ctx, 4), dp(ctx, 22), dp(ctx, 4), dp(ctx, 8))
    }

    fun note(ctx: Context, s: String) = text(ctx, s, 13f, pal(ctx).muted, bold = false).apply {
        setLineSpacing(0f, 1.15f)
    }

    /** A settings-style row: icon tile, label (+ optional second line), optional value, optional chevron. */
    fun row(
        ctx: Context, res: Int, toneName: String, label: String, value: String? = null,
        sub: String? = null, valueColor: Int? = null, chevron: Boolean = true, danger: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val p = pal(ctx)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 58)
            setPaddingRelative(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
            addView(tile(ctx, res, if (danger) "red" else toneName))
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(ctx, 12), 0, dp(ctx, 8), 0)
                addView(text(ctx, label, 15f, if (danger) p.danger else p.ink))
                if (!sub.isNullOrEmpty()) addView(text(ctx, sub, 13f, p.muted, bold = false))
            }
            addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (!value.isNullOrEmpty()) addView(text(ctx, value, 14f, valueColor ?: p.muted).apply {
                maxLines = 1; maxWidth = dp(ctx, 150); ellipsize = android.text.TextUtils.TruncateAt.END
                setPaddingRelative(0, 0, dp(ctx, 4), 0)
            })
            if (chevron) addView(icon(ctx, R.drawable.ui_chevron, p.muted), LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)))
            if (onClick != null) {
                isClickable = true; isFocusable = true
                contentDescription = if (value.isNullOrEmpty()) label else "$label, $value"
                ripple(ctx, this)
                setOnClickListener { onClick() }
            }
        }
    }

    /** A row with an on/off switch and an optional second line. */
    fun switchRow(
        ctx: Context, res: Int, toneName: String, label: String, sub: String?, checked: Boolean,
        enabled: Boolean = true, onChange: (Boolean) -> Unit,
    ): LinearLayout {
        val p = pal(ctx)
        val sw = Switch(ctx).apply {
            isChecked = checked; isEnabled = enabled; contentDescription = label
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(p.accent, if (isDark(ctx)) c(0xFFC3C9DA) else c(0xFFFFFFFF)))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(p.accentSoft, p.track))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 58)
            setPaddingRelative(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
            addView(tile(ctx, res, toneName))
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(ctx, 12), 0, dp(ctx, 8), 0)
                addView(text(ctx, label, 15f, p.ink))
                if (!sub.isNullOrEmpty()) addView(text(ctx, sub, 13f, p.muted, bold = false))
            }
            addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sw)
            if (enabled) { isClickable = true; ripple(ctx, this); setOnClickListener { sw.toggle() } }
        }
    }

    fun divider(ctx: Context, indentDp: Int = 60) = View(ctx).apply {
        setBackgroundColor(pal(ctx).divider)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(ctx, 1)).apply { marginStart = dp(ctx, indentDp) }
    }

    /** A rounded card holding rows separated by thin dividers. */
    fun group(ctx: Context, rows: List<View>): LinearLayout = card(ctx).apply {
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(divider(ctx))
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    /** A small rounded pill: icon + text. */
    fun chip(ctx: Context, res: Int, iconColor: Int, label: String): LinearLayout {
        val p = pal(ctx)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(p.card, dp(ctx, 999).toFloat(), p.border, dp(ctx, 1))
            setPaddingRelative(dp(ctx, 11), dp(ctx, 7), dp(ctx, 13), dp(ctx, 7))
            addView(icon(ctx, res, iconColor), LinearLayout.LayoutParams(dp(ctx, 17), dp(ctx, 17)))
            addView(text(ctx, label, 14f, p.ink).apply { setPaddingRelative(dp(ctx, 6), 0, 0, 0) })
        }
    }

    /** A clickable rounded button with an optional icon. */
    fun button(
        ctx: Context, label: String, res: Int?, bg: Int, fg: Int, heightDp: Int = 50,
        stroke: Int = 0, onClick: () -> Unit,
    ): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        background = rounded(bg, dp(ctx, 15).toFloat(), stroke, if (stroke != 0) dp(ctx, 1) else 0)
        minimumHeight = dp(ctx, heightDp)
        setPaddingRelative(dp(ctx, 14), 0, dp(ctx, 14), 0)
        if (res != null) addView(icon(ctx, res, fg), LinearLayout.LayoutParams(dp(ctx, 19), dp(ctx, 19)).apply { marginEnd = dp(ctx, 7) })
        addView(text(ctx, label, 15f, fg).apply { maxLines = 1 })
        isClickable = true; isFocusable = true; contentDescription = label
        ripple(ctx, this)
        clipToOutline = true
        setOnClickListener { onClick() }
    }

    fun primary(ctx: Context, label: String, res: Int? = null, onClick: () -> Unit) =
        button(ctx, label, res, pal(ctx).accent, c(0xFFFFFFFF), onClick = onClick)

    fun secondary(ctx: Context, label: String, res: Int? = null, onClick: () -> Unit) =
        pal(ctx).let { p -> button(ctx, label, res, p.accentSoft, p.accentInk, onClick = onClick) }

    /** A 46dp square icon button (e.g. the settings gear in a corner). */
    fun iconButton(ctx: Context, res: Int, desc: String, onClick: () -> Unit): FrameLayout {
        val p = pal(ctx)
        return FrameLayout(ctx).apply {
            background = rounded(p.card, dp(ctx, 14).toFloat(), p.border, dp(ctx, 1))
            addView(icon(ctx, res, p.ink), FrameLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46))
            isClickable = true; isFocusable = true; contentDescription = desc
            ripple(ctx, this); clipToOutline = true
            setOnClickListener { onClick() }
        }
    }

    /** Big screen title with an optional settings gear in the corner. */
    fun header(a: Activity, title: String, gear: Boolean, sub: String? = null): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(a, title, 28f, pal(a).ink))
            if (sub != null) addView(text(a, sub, 14f, pal(a).muted, bold = false).apply { setPaddingRelative(0, dp(a, 2), 0, 0) })
        }
        addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (gear) addView(iconButton(a, R.drawable.ui_gear, "Settings") {
            a.startActivity(Intent(a, SettingsActivity::class.java))
        })
    }

    /** Title row with a back arrow, for screens opened on top (Settings, Import, …). */
    fun backHeader(a: Activity, title: String): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(a, R.drawable.ui_back, "Back") { a.finish() })
        addView(text(a, title, 24f, pal(a).ink).apply { setPaddingRelative(dp(a, 14), 0, 0, 0) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    /** A text field in the app's style. */
    fun field(ctx: Context, hint: String, multiLine: Boolean = false): EditText {
        val p = pal(ctx)
        return EditText(ctx).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(p.ink)
            setHintTextColor(p.hint)
            background = rounded(p.field, dp(ctx, 12).toFloat(), p.border, dp(ctx, 1))
            setPaddingRelative(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            inputType = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                else InputType.TYPE_CLASS_TEXT
            if (multiLine) gravity = Gravity.TOP or Gravity.START
        }
    }

    /** A rounded progress bar (fraction 0..1). */
    fun bar(ctx: Context, fraction: Float, fill: Int, track: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(track, dp(ctx, 5).toFloat())
        val f = fraction.coerceIn(0f, 1f)
        addView(View(ctx).apply { background = rounded(fill, dp(ctx, 5).toFloat()) }, LinearLayout.LayoutParams(0, dp(ctx, 10), f))
        addView(View(ctx), LinearLayout.LayoutParams(0, dp(ctx, 10), 1f - f))
    }

    /** A segmented tab control (e.g. Personal · Work · Email · Other). */
    fun segmented(ctx: Context, options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val p = pal(ctx)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(p.track, dp(ctx, 14).toFloat())
            setPaddingRelative(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            options.forEachIndexed { i, label ->
                val on = i == selected
                addView(text(ctx, label, 14f, if (on) p.ink else p.muted).apply {
                    gravity = Gravity.CENTER; maxLines = 1
                    setPaddingRelative(dp(ctx, 4), dp(ctx, 9), dp(ctx, 4), dp(ctx, 9))
                    if (on) background = rounded(p.card, dp(ctx, 11).toFloat())
                    isClickable = true; isSelected = on; contentDescription = label
                    setOnClickListener { if (!on) onPick(i) }
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
        }
    }

    /** A dialog in the app's light or dark look. */
    fun dialog(ctx: Context): AlertDialog.Builder = AlertDialog.Builder(ctx,
        if (isDark(ctx)) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)

    /** Wraps a view with dialog-style side padding. */
    fun padded(ctx: Context, v: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(ctx, 22), dp(ctx, 8), dp(ctx, 22), 0)
        addView(v, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    // ----- bottom tab bar -----

    private class Tab(val key: String, val res: Int, val label: String, val target: Class<*>)

    private val TABS = listOf(
        Tab("home", R.drawable.ui_home, "Home", MainActivity::class.java),
        Tab("dictionary", R.drawable.ui_book, "Dictionary", DictionaryActivity::class.java),
        Tab("shortcuts", R.drawable.ui_zap, "Shortcuts", ShortcutsActivity::class.java),
        Tab("style", R.drawable.ui_type, "Style", StylesActivity::class.java),
    )

    /** Home is the base screen; the other tabs open on top of it and replace each other. */
    private fun go(a: Activity, from: String, to: Tab) {
        if (from == to.key) return
        if (to.key == "home") { a.finish(); a.overridePendingTransition(0, 0); return }
        a.startActivity(Intent(a, to.target))
        a.overridePendingTransition(0, 0)
        if (from != "home") a.finish()
    }

    fun bottomNav(a: Activity, active: String): LinearLayout {
        val p = pal(a)
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(p.card)
            setPaddingRelative(dp(a, 6), dp(a, 8), dp(a, 6), dp(a, 12))
        }
        for (t in TABS) {
            val on = t.key == active
            val item = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true; isFocusable = true
                contentDescription = t.label
                isSelected = on
                setOnClickListener { go(a, active, t) }
            }
            val pill = FrameLayout(a).apply { if (on) background = rounded(p.accentSoft, dp(a, 15).toFloat()) }
            pill.addView(icon(a, t.res, if (on) p.accentInk else p.muted), FrameLayout.LayoutParams(dp(a, 21), dp(a, 21), Gravity.CENTER))
            item.addView(pill, LinearLayout.LayoutParams(dp(a, 58), dp(a, 30)))
            item.addView(text(a, t.label, 12f, if (on) p.accentInk else p.muted).apply {
                gravity = Gravity.CENTER; setPaddingRelative(0, dp(a, 3), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            bar.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(a).apply { setBackgroundColor(p.border) }, LinearLayout.LayoutParams(MATCH, dp(a, 1)))
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    /** Status and navigation bar colours to match the theme. */
    @Suppress("DEPRECATION")
    fun systemBars(a: Activity) {
        val p = pal(a)
        a.window.statusBarColor = p.bg
        a.window.navigationBarColor = p.card
        var flags = 0
        if (!isDark(a)) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        a.window.decorView.systemUiVisibility = flags
    }

    /** A tab screen: scrolling content above the bottom tab bar. */
    fun tabScreen(a: Activity, content: View, active: String) {
        systemBars(a)
        val p = pal(a)
        val scroll = ScrollView(a).apply { setBackgroundColor(p.bg); isFillViewport = true; addView(content) }
        val outer = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        outer.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        outer.addView(bottomNav(a, active), LinearLayout.LayoutParams(MATCH, WRAP))
        a.setContentView(outer)
    }

    /** A screen opened on top of a tab (no tab bar). */
    fun plainScreen(a: Activity, content: View) {
        systemBars(a)
        val p = pal(a)
        a.setContentView(ScrollView(a).apply { setBackgroundColor(p.bg); isFillViewport = true; addView(content) })
    }
}
