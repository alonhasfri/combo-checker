package com.voicebubble

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.Rect

/**
 * Optional helper that types the text into whatever text box is selected in any app.
 * It only acts when you finish a recording; it reads nothing else.
 */
class InsertService : AccessibilityService() {

    companion object {
        @Volatile var instance: InsertService? = null
        val isOn get() = instance != null
    }

    private var lastEditable: AccessibilityNodeInfo? = null

    // The bubble moves on top of the keyboard while this service is on (see BubbleService.addBubble).
    override fun onServiceConnected() { instance = this; BubbleService.instance?.reattach() }
    override fun onDestroy() { instance = null; BubbleService.instance?.reattach(); super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        BubbleService.instance?.reattach()
        return super.onUnbind(intent)
    }
    override fun onInterrupt() {}

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val checkFix = Runnable { checkForFix() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Remember the last text box that got focus, in case the app doesn't report focus later.
        val src = event?.source ?: return
        if (src.isEditable) lastEditable = src
        // You edited the text box it just typed into: once you pause, see if you corrected a word.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && last?.node == src) {
            main.removeCallbacks(checkFix)
            main.postDelayed(checkFix, 1500)
        }
    }

    /** "Go Joe" fixed by hand to "Gojo": offer to add "Gojo" to the dictionary. */
    private fun checkForFix() {
        val l = last ?: return
        if (!Prefs(this).learnFixes) return
        if (!runCatching { l.node.refresh() }.getOrDefault(false)) return
        val now = if (l.node.isShowingHintText) "" else l.node.text?.toString().orEmpty()
        val (heard, meant) = Learn.correctionPair(l.newText, now, l.at, l.at + l.piece.length) ?: return
        last = null   // one offer per dictation (and undo no longer applies to edited text)
        if (Adapt.fixes(this).any { it.say.equals(heard, true) && it.write == meant }) return
        BubbleService.instance?.offerWord(heard, meant)
    }

    /** Screen y of the keyboard's top edge, or -1 when no keyboard is open. */
    fun keyboardTop(): Int {
        val w = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return -1
        val r = Rect()
        w.getBoundsInScreen(r)
        return if (r.height() > 0) r.top else -1
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val f = runCatching { rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (f != null && f.isEditable) return f
        // Some apps keep the text box in another window (e.g. a dialog).
        for (w in runCatching { windows }.getOrNull().orEmpty()) {
            val n = runCatching { w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            if (n != null && n.isEditable) return n
        }
        return lastEditable?.takeIf { it.refresh() && it.isEditable && it.isFocused }
    }

    /** What the last dictation changed, so it can be undone or replaced (rewrite). */
    private class LastInsert(val node: AccessibilityNodeInfo, val oldText: String, val newText: String,
                             val at: Int, val piece: String)
    private var last: LastInsert? = null

    private fun setText(node: AccessibilityNodeInfo, text: String, cursor: Int): Boolean {
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
        if (ok) node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
        return ok
    }

    /** The text box still holds exactly what the last dictation left in it. */
    private fun lastStillThere(): LastInsert? {
        val l = last ?: return null
        if (!runCatching { l.node.refresh() }.getOrDefault(false)) return null
        val now = if (l.node.isShowingHintText) "" else l.node.text?.toString().orEmpty()
        return if (now == l.newText) l else null
    }

    val canUndo get() = lastStillThere() != null

    /** Takes the last dictation back out. */
    fun undo(): Boolean {
        val l = lastStillThere() ?: return false
        val ok = setText(l.node, l.oldText, l.at)
        if (ok) last = null
        return ok
    }

    /** Swaps the last dictation for a new version (after "rewrite" or translate). */
    fun replaceLast(text: String): Boolean {
        val l = lastStillThere() ?: return false
        val lead = if (l.piece.startsWith(" ")) " " else ""
        val trail = if (l.piece.endsWith(" ") && l.piece.isNotBlank()) " " else ""
        val piece = lead + text + trail
        val newText = l.oldText.substring(0, l.at) + piece + l.newText.substring(l.at + l.piece.length)
        if (!setText(l.node, newText, l.at + piece.length)) return false
        last = LastInsert(l.node, l.oldText, newText, l.at, piece)
        return true
    }

    /** The text you can see on screen (for reply suggestions), newest last, trimmed to a sensible size. */
    fun screenText(): String {
        val lines = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || lines.size > 400) return
            if (!n.isEditable) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrEmpty() && t.length > 1) lines += t
            }
            for (i in 0 until n.childCount) walk(runCatching { n.getChild(i) }.getOrNull(), depth + 1)
        }
        walk(rootInActiveWindow, 0)
        val sb = StringBuilder()
        for (l in lines.asReversed()) {
            if (sb.length + l.length > 2500) break
            sb.insert(0, l + "\n")
        }
        return sb.toString().trim()
    }

    /** Puts [text] at the cursor of the selected text box. False when there is no text box. */
    fun insert(text: String): Boolean {
        val node = focusedEditable() ?: return false
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0 || start > current.length || end > current.length) { start = current.length; end = current.length }
        if (start > end) { val t = start; start = end; end = t }

        val before = current.substring(0, start)
        val after = current.substring(end)
        var piece = text
        if (before.isNotEmpty() && !before.last().isWhitespace()) piece = " $piece"
        if (after.isNotEmpty() && !after.first().isWhitespace()) piece = "$piece "
        val newText = before + piece + after

        if (setText(node, newText, before.length + piece.length)) {
            last = LastInsert(node, current, newText, before.length, piece)
            return true
        }
        // Fallback for text boxes that refuse "set text": paste instead.
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Voice Bubble", piece))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}
