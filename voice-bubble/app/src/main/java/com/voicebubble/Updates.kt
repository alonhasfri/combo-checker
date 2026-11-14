package com.voicebubble

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks for a newer Voice Bubble build on GitHub. Every build is signed with the same key,
 * so the new APK installs as an update: no uninstalling, the model and your words stay.
 */
object Updates {
    class Release(val build: Int, val apkUrl: String, val pageUrl: String)

    val current get() = BuildConfig.VERSION_CODE
    private val assetName get() = if (BuildConfig.AUTOTYPE) "voice-bubble-autotype.apk" else "voice-bubble.apk"
    private fun sp(c: Context) = c.getSharedPreferences("voice", Context.MODE_PRIVATE)

    /** Blocking network call. Null when offline or nothing found. */
    fun fetchNewest(): Release? = try {
        val conn = URL("https://api.github.com/repos/alonhasfri/combo-checker/releases?per_page=50").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        var best: Release? = null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val n = r.optString("tag_name").removePrefix("voice-bubble-").toIntOrNull() ?: continue
            if (!r.optString("tag_name").startsWith("voice-bubble-")) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name") == assetName && (best == null || n > best.build)) {
                    best = Release(n, a.optString("browser_download_url"), r.optString("html_url"))
                }
            }
        }
        best
    } catch (e: Exception) { null }

    /** Checks at most once an hour unless [force]. Calls back on a background thread. */
    fun check(c: Context, force: Boolean, done: (Release?) -> Unit) {
        val last = sp(c).getLong("updCheckedAt", 0L)
        if (!force && System.currentTimeMillis() - last < 3600_000L) {
            val b = sp(c).getInt("updBuild", 0)
            done(if (b > current) Release(b, sp(c).getString("updApk", "")!!, sp(c).getString("updPage", "")!!) else null)
            return
        }
        Thread {
            val r = fetchNewest()
            if (r != null) sp(c).edit().putLong("updCheckedAt", System.currentTimeMillis())
                .putInt("updBuild", r.build).putString("updApk", r.apkUrl).putString("updPage", r.pageUrl).apply()
            done(if (r != null && r.build > current) r else null)
        }.start()
    }
}
