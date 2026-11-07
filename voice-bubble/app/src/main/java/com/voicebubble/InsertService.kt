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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Remember the last text box that got focus, in case the app doesn't report focus later.
        val src = event?.source ?: return
        if (src.isEditable) lastEditable = src
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

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val cursor = before.length + piece.length
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            })
            return true
        }
        // Fallback for text boxes that refuse "set text": paste instead.
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Voice Bubble", piece))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}
