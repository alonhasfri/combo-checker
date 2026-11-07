package com.voicebubble

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton

/** Style tab: how your words are written in each kind of app, with live examples. */
class StylesActivity : Screen() {

    private var cat = Styles.Category.PERSONAL

    override fun render() {
        val page = Ui.page(this)
        page.addView(Ui.header(this, "Style", gear = true, sub = "How your words are written in each kind of app"))

        // Without app detection every app counts as "Other".
        if (!AppDetector.canDetect(this)) {
            page.addView(Ui.card(this).apply {
                background = Ui.rounded(Ui.tone(this@StylesActivity, "gold").first, dp(20).toFloat())
                addView(Ui.row(this@StylesActivity, R.drawable.ui_shield, "gold", "Let it see which app you're in",
                    sub = "Needed for styles per app. If it's greyed out: App info → ⋮ → Allow restricted settings.") {
                    Setup.openUsage(this@StylesActivity)
                })
            }, Ui.lp(this, 16))
        }

        val cats = Styles.Category.values().toList()
        page.addView(Ui.segmented(this, cats.map { it.title }, cats.indexOf(cat)) { i -> cat = cats[i]; render() }, Ui.lp(this, 16))

        // Installed apps in this category.
        val icons = (Styles.apps[cat] ?: emptyList()).mapNotNull { pkg -> runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull() }
        if (icons.isNotEmpty()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            icons.take(7).forEach { ic ->
                row.addView(ImageView(this).apply { setImageDrawable(ic) },
                    LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(8) })
            }
            page.addView(row, Ui.lp(this, 16))
        }
        page.addView(Ui.note(this, cat.where).apply { setPaddingRelative(dp(2), dp(8), 0, 0) })

        val chosen = Styles.get(this, cat)
        val sample = Styles.samples.getValue(cat)
        Styles.Style.values().forEach { s ->
            val sel = s == chosen
            val c = Ui.paddedCard(this)
            (c.background as GradientDrawable).setStroke(dp(if (sel) 2 else 1), if (sel) p.accent else p.border)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titles.addView(Ui.text(this, s.title, 18f, p.ink))
            titles.addView(Ui.text(this, s.subtitle, 13f, p.muted, bold = false))
            head.addView(titles, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            head.addView(RadioButton(this).apply { isChecked = sel; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            c.addView(head)

            // Example bubble, like a chat message.
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
                background = Ui.rounded(p.bg, dp(14).toFloat())
            }
            bubble.addView(Ui.text(this, "Y", 12f, p.accentInk).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(p.accentSoft) }
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            bubble.addView(Ui.text(this, Styles.apply(sample, s, cat), 14f, p.ink, bold = false).apply {
                setPaddingRelative(dp(10), dp(2), 0, 0); setLineSpacing(0f, 1.15f)
            }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            c.addView(bubble, Ui.lp(this, 12))

            c.isClickable = true
            c.contentDescription = "${s.title} ${s.subtitle}"
            Ui.ripple(this, c)
            c.setOnClickListener { Styles.set(this, cat, s); render() }
            page.addView(c, Ui.lp(this, 12))
        }
        // Your own choice for particular apps (beats the category above).
        page.addView(Ui.sectionTitle(this, "Specific apps"))
        val overrides = Styles.appStyles(this)
        val rows = overrides.map { (pkg, st) ->
            Ui.row(this, R.drawable.ui_layout, "teal", appName(pkg), value = st.title.trimEnd('.', '!')) { pickStyle(pkg) }
        }.toMutableList<android.view.View>()
        rows += Ui.row(this, R.drawable.ui_plus, "blue", "Add an app", sub = "Give one app its own style, e.g. Discord → gen z") { pickApp() }
        page.addView(Ui.group(this, rows))

        page.addView(Ui.note(this, "Styles are made for English. In other languages only the caps and punctuation change.")
            .apply { gravity = Gravity.CENTER; setPaddingRelative(0, dp(16), 0, 0) })

        Ui.tabScreen(this, page, "style")
    }

    private fun appName(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    /** Lists the apps on your phone (the ones with an icon in the app drawer). */
    private fun pickApp() {
        val launch = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launch, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        Ui.dialog(this).setTitle("Choose an app").setItems(apps.map { it.second }.toTypedArray()) { _, i ->
            pickStyle(apps[i].first)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickStyle(pkg: String) {
        val styles = Styles.Style.values()
        val names = styles.map { "${it.title.trimEnd('.', '!')} · ${it.subtitle}" } + "Use the category's style"
        Ui.dialog(this).setTitle(appName(pkg)).setItems(names.toTypedArray()) { _, i ->
            Styles.setAppStyle(this, pkg, styles.getOrNull(i))
            render()
        }.setNegativeButton("Cancel", null).show()
    }
}
