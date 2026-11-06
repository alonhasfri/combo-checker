package com.voicebubble

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared look for the app's screens: light background, white cards, plain buttons. */
open class BaseActivity : Activity() {
    protected val d by lazy { resources.displayMetrics.density }
    protected fun dp(v: Int) = (v * d).toInt()
    protected val grey = Color.rgb(110, 116, 130)

    protected fun page(build: (LinearLayout) -> Unit) {
        window.statusBarColor = Color.rgb(246, 247, 250)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(40))
        }
        build(col)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 247, 250))
            addView(col)
        })
    }

    protected fun text(s: String, size: Float, bold: Boolean = false, color: Int = Color.rgb(24, 28, 38)) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    protected fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 16 * d }
            elevation = 2 * d
        }
        if (title.isNotEmpty()) c.addView(text(title, 17f, bold = true).apply { setPadding(0, 0, 0, dp(6)) })
        parent.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        return c
    }

    protected fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
