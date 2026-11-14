package com.voicebubble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** The setup steps (model, permissions) and how to fix each one. Shared by Home and Settings. */
object Setup {
    class Step(
        val id: String, val title: String, val sub: String, val icon: Int, val tone: String,
        val required: Boolean, val done: Boolean, val fix: (Activity) -> Unit,
    )

    fun hasMic(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun hasOverlay(c: Context) = Settings.canDrawOverlays(c)
    fun modelReady(c: Context): Boolean {
        val p = Prefs(c)
        return Models.isDownloaded(c, Models.byId(p.modelId)) ||
            (p.engine == "live" && Live.isDownloaded(c, Live.byId(p.liveModelId)))
    }

    fun askMic(a: Activity) {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        a.requestPermissions(perms.toTypedArray(), 1)
    }

    fun openOverlay(a: Activity) {
        runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}"))) }
    }

    fun openUsage(a: Activity) {
        runCatching { a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${a.packageName}"))) }
            .onFailure { runCatching { a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
    }

    fun openAccessibility(a: Activity) {
        runCatching { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    fun steps(c: Context): List<Step> {
        val m = Models.byId(Prefs(c).modelId)
        val list = mutableListOf(
            Step("model", "Download the speech model", "${m.title.substringBefore(" (")} · ${m.bytes / 1_000_000} MB, one time",
                R.drawable.ui_download, "blue", true, modelReady(c)) { a -> a.startActivity(Intent(a, ModelActivity::class.java)) },
            Step("mic", "Allow the microphone", "So it can hear you",
                R.drawable.ui_mic, "red", true, hasMic(c)) { a -> askMic(a) },
            Step("overlay", "Allow showing over other apps", "For the floating bubble",
                R.drawable.ui_layout, "purple", true, hasOverlay(c)) { a -> openOverlay(a) },
            Step("usage", "Let it see which app you're in", "Optional · for styles per app",
                R.drawable.ui_type, "teal", false, AppDetector.canDetect(c)) { a -> openUsage(a) },
        )
        if (BuildConfig.AUTOTYPE) list += Step("type", "Turn on typing into apps", "Optional · types the text for you",
            R.drawable.ui_wand, "gold", false, InsertService.isOn) { a -> openAccessibility(a) }
        return list
    }

    fun readyToStart(c: Context) = modelReady(c) && hasMic(c) && hasOverlay(c)
}
