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
        page.addView(Ui.note(this, "Give an app its own style, language or length limit (the longest message it sends). " +
                "It beats the settings above.").apply { setPaddingRelative(dp(6), 0, dp(6), dp(12)) })
        val styles = Styles.appStyles(this)
        val langs = Styles.appLangs(this)
        val limits = Styles.appLimits(this)
        // Roblox is listed when it's on your phone, since it gets 200 characters by itself.
        val roblox = listOfNotNull(Limit.ROBLOX.takeIf { installed(it) })
        val rows = (styles.keys + langs.keys + limits.keys + roblox).distinct().map { pkg ->
            val parts = listOfNotNull(styles[pkg]?.title?.trimEnd('.', '!'),
                langs[pkg]?.let { code -> Langs.all.firstOrNull { it.first == code }?.second ?: code })
            Ui.row(this, R.drawable.ui_layout, "teal", appName(pkg), value = parts.joinToString(" · ").ifEmpty { null },
                sub = "Length limit: " + limitLabel(pkg)) { appOptions(pkg) }
        }.toMutableList<android.view.View>()
        rows += Ui.row(this, R.drawable.ui_plus, "blue", "Add an app",
            sub = "e.g. Discord → gen z, Roblox → 200 characters") { pickApp() }
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
            appOptions(apps[i].first)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun installed(pkg: String) = runCatching { packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    /** "200 characters (automatic)", "150 characters", "none"… */
    private fun limitLabel(pkg: String): String {
        val own = Styles.appLimits(this)[pkg]
        val all = Prefs(this).maxChars
        return when {
            own != null && own > 0 -> "$own characters"
            own != null -> "none"
            pkg == Limit.ROBLOX -> "${Limit.ROBLOX_CHARS} characters (automatic)"
            all > 0 -> "$all characters (same as all apps)"
            else -> "none"
        }
    }

    private fun appOptions(pkg: String) {
        val style = Styles.appStyles(this)[pkg]?.title?.trimEnd('.', '!') ?: "same as its category"
        val lang = Styles.appLangs(this)[pkg]?.let { code -> Langs.all.firstOrNull { it.first == code }?.second ?: code } ?: "same as everywhere"
        Ui.dialog(this).setTitle(appName(pkg)).setItems(arrayOf("Style: $style", "Language: $lang",
            "Length limit: ${limitLabel(pkg)}", "Remove (use the defaults)")) { _, i ->
            when (i) {
                0 -> pickStyle(pkg)
                1 -> pickLang(pkg)
                2 -> pickLimit(this, pkg) { render() }
                else -> { Styles.setAppStyle(this, pkg, null); Styles.setAppLang(this, pkg, null); Styles.setAppLimit(this, pkg, null); render() }
            }
        }.setNegativeButton("Cancel", null).show()
    }

    /** The language you speak in this app (needs an "all languages" model for anything but English). */
    private fun pickLang(pkg: String) {
        val names = Langs.all.map { it.second } + "Same as everywhere else"
        Ui.dialog(this).setTitle("Language in ${appName(pkg)}").setItems(names.toTypedArray()) { _, i ->
            Styles.setAppLang(this, pkg, Langs.all.getOrNull(i)?.first)
            val code = Langs.all.getOrNull(i)?.first
            if (code != null && code != "en" && Models.all.none { !it.englishOnly && Models.isDownloaded(this, it) })
                toast("Also download an \"all languages\" model (Home → Speech model) for this to work")
            render()
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
