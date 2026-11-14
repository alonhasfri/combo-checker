package com.voicebubble

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a model file once. Resumes a partial download if one is left over. */
object Downloader {
    @Volatile var cancelled = false

    /** onProgress gets 0..1. Returns null on success or an error message. */
    fun download(url: String, target: File, expected: Long, onProgress: (Float) -> Unit): String? {
        cancelled = false
        val part = File(target.path + ".part")
        try {
            var conn: HttpURLConnection
            var u = URL(url)
            var redirects = 0
            while (true) {
                conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    if (part.exists() && part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
                }
                val code = conn.responseCode
                if (code in 300..399 && redirects < 8) {
                    val loc = conn.getHeaderField("Location") ?: return "Bad redirect"
                    u = URL(u, loc)
                    conn.disconnect()
                    redirects++
                    continue
                }
                break
            }
            val code = conn.responseCode
            val append = code == 206
            if (code != 200 && code != 206) return "Server answered $code"
            if (!append) part.delete()
            var done = if (append) part.length() else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var last = 0L
                    while (true) {
                        if (cancelled) return "Cancelled"
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 1_000_000) { last = done; onProgress(done.toFloat() / expected) }
                    }
                }
            }
            if (part.length() != expected) return "Download incomplete, try again"
            if (!part.renameTo(target)) return "Couldn't save the file"
            onProgress(1f)
            return null
        } catch (e: Exception) {
            return "No connection (${e.javaClass.simpleName}). Try again"
        }
    }
}
