package com.voicebubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

/**
 * Keeps the floating microphone on screen. Tap: start listening. Tap again: stop, turn the speech
 * into text on the phone, and type it into the selected text box (or copy it).
 * Long-press cancels a recording. Drag to move.
 */
class BubbleService : Service() {

    companion object {
        const val ACTION_STOP = "com.voicebubble.STOP"
        private const val CHANNEL = "bubble"
        @Volatile var running = false
        @Volatile var instance: BubbleService? = null
        /** Whether the bubble is currently drawn on top of the keyboard (autotype version with typing on). */
        @Volatile var drawnOverKeyboard = false
        /** For the main screen's "keyboard detection" status line. */
        @Volatile var keyboardNow = false
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager       // the bubble's window manager (app, or accessibility service)
    private lateinit var appWm: WindowManager    // the app's own (for the keyboard detector)
    /** True when the bubble is shown by the accessibility service, which draws it on top of the keyboard. */
    private var onTopOfKeyboard = false
    private lateinit var prefs: Prefs
    private var view: BubbleView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val recorder = Recorder()
    @Volatile private var working = false
    private var watcher: KeyboardWatcher? = null
    private var keyboardOpen = false
    private var keyboardTop: Int? = null
    private var homeY = 0          // where you put the bubble when no keyboard is open
    private var lastKbTop: Int? = null  // last known keyboard top, in case a reading lacks it
    private var touchDragging = false   // don't move the bubble under your finger
    private var screenOffset = 0   // window y -> screen y
    private var size = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Must be started while the app is on screen: that is what gives the bubble microphone access.
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Open Voice Bubble and press Start", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        instance = this
        prefs = Prefs(this)
        appWm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (view == null) {
            addBubble()
            watcher = KeyboardWatcher(this, appWm) { open, top ->
                keyboardOpen = open
                keyboardTop = top
                keyboardNow = open
                if (open && !prefs.keyboardSeen) prefs.keyboardSeen = true
                updateBubble()
            }.also { it.start() }
        }
        applySize()
        updateBubble()
        // Load the model now so the first transcription doesn't wait for it.
        Thread { Whisper.ensureLoaded(Models.file(this, Models.byId(prefs.modelId)).path) }.start()
        // Not sticky: if Android kills it, it can't get the microphone back without the app anyway.
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating microphone", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Voice Bubble is on")
            .setContentText("Tap the floating microphone to talk")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Hide bubble", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun addBubble() {
        // Normal apps' floating windows are always drawn under the keyboard. Only an accessibility
        // service may draw on top of it, so when "Type into apps" is on, the bubble is shown through it.
        val acc = InsertService.instance
        val accWm = acc?.let { runCatching { it.getSystemService(WINDOW_SERVICE) as WindowManager }.getOrNull() }
        onTopOfKeyboard = accWm != null
        drawnOverKeyboard = onTopOfKeyboard
        wm = accWm ?: appWm
        val dm = resources.displayMetrics
        size = wantedSize()
        lp = WindowManager.LayoutParams(
            size, size,
            if (onTopOfKeyboard) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the keyboard and the text box you're typing in stay as they are.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX.takeIf { it >= 0 } ?: (dm.widthPixels - size)
            y = prefs.bubbleY.takeIf { it >= 0 } ?: (dm.heightPixels / 3)
        }
        homeY = lp.y
        val v = BubbleView(this)
        attachTouch(v)
        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> measureOffset() }
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            if (!onTopOfKeyboard) throw e
            // The accessibility service went away in the meantime: fall back to a normal floating window.
            onTopOfKeyboard = false
            drawnOverKeyboard = false
            wm = appWm
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            wm.addView(v, lp)
        }
        view = v
    }

    /** Re-creates the bubble when "Type into apps" is switched on or off, so it moves over or under the keyboard. */
    fun reattach() {
        main.post {
            if (!running || recorder.isRecording || working) return@post
            val old = view ?: return@post
            val wantOnTop = InsertService.instance != null
            if (wantOnTop == onTopOfKeyboard) return@post
            runCatching { wm.removeView(old) }
            view = null
            addBubble()
            updateBubble()
        }
    }

    private fun attachTouch(v: BubbleView) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var longPressed = false
        val longPress = Runnable { longPressed = true; onLongPress() }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    dragging = false; longPressed = false; touchDragging = false
                    main.postDelayed(longPress, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) { dragging = true; touchDragging = true; main.removeCallbacks(longPress) }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    touchDragging = false
                    if (dragging) {
                        prefs.bubbleX = lp.x
                        val dockY = dockedY()
                        if (dockY != null && prefs.dockAboveKeyboard) {
                            // Docked above the keyboard: remember how high above it you put it.
                            // Drawn on top of the keyboard, it may sit on the keys; otherwise only above them.
                            val lift = (dockY - lp.y).coerceAtLeast(if (onTopOfKeyboard) -resources.displayMetrics.heightPixels else 0)
                            prefs.dockLift = lift
                            lp.y = dockY - lift
                            runCatching { wm.updateViewLayout(v, lp) }
                        } else { prefs.bubbleY = lp.y; homeY = lp.y }
                    }
                    else if (!longPressed) onTap()
                }
                MotionEvent.ACTION_CANCEL -> { main.removeCallbacks(longPress); touchDragging = false; updateBubble() }
            }
            true
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (!recorder.isRecording) return
            v.level = recorder.level
            v.seconds = recorder.seconds
            main.postDelayed(this, 50)
        }
    }

    private fun onTap() {
        when {
            working -> toast("Still writing out the last one…")
            recorder.isRecording -> finishRecording()
            else -> startRecording()
        }
    }

    private fun onLongPress() {
        when {
            recorder.isRecording -> {
                recorder.cancel()
                setState(BubbleView.State.IDLE)
                buzz(60)
                toast("Cancelled")
            }
            working -> { Whisper.abort(); toast("Cancelled") }
            else -> toast("Drag to move · hide it from the notification")
        }
    }

    private fun startRecording() {
        val model = Models.byId(prefs.modelId)
        if (!Models.isDownloaded(this, model)) {
            toast("Open Voice Bubble and download the speech model first")
            return
        }
        try {
            recorder.onMaxReached = { main.post { if (recorder.isRecording) finishRecording() } }
            recorder.start()
        } catch (e: Exception) {
            toast("Can't use the microphone. Open Voice Bubble and press Start again")
            return
        }
        buzz(30)
        setState(BubbleView.State.RECORDING)
        main.post(ticker)
    }

    private fun finishRecording() {
        val audio = recorder.stop()
        buzz(30)
        if (!hasSpeech(audio)) {
            setState(BubbleView.State.IDLE)
            toast("Didn't hear anything")
            return
        }
        working = true
        setState(BubbleView.State.WORKING)
        val model = Models.file(this, Models.byId(prefs.modelId)).path
        val lang = prefs.lang
        val prompt = Words.prompt(this)
        // The style of the app you're typing in (chat, work, email, other).
        val app = AppDetector.current(this)
        val cat = Styles.categoryOf(app)
        val seconds = audio.size / Recorder.RATE.toFloat()
        val style = Styles.get(this, cat)
        Thread {
            val raw = runCatching { Whisper.transcribe(model, audio, lang, prompt) }.getOrNull()
            // Style first, then your dictionary spellings and shortcuts (those are kept exactly as you wrote them).
            val text = raw?.let { clean(it) }?.takeIf { it.isNotEmpty() }
                ?.let { Styles.apply(it, style, cat) }
                ?.let { Words.apply(this, it, capitalize = style != Styles.Style.VERY_CASUAL) }
            main.post {
                working = false
                setState(BubbleView.State.IDLE)
                when {
                    raw == null -> toast("Couldn't turn that into text")
                    text.isNullOrEmpty() -> toast("Didn't catch any words")
                    else -> {
                        deliver(text)
                        Stats.record(this, text, seconds, app)
                    }
                }
            }
        }.apply { name = "whisper" }.start()
    }

    /** Types it into the selected text box; otherwise puts it on the clipboard. */
    private fun deliver(text: String) {
        val typed = InsertService.instance?.let { runCatching { it.insert(text) }.getOrDefault(false) } ?: false
        if (typed) {
            buzz(15)
            return
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", text))
        buzz(15)
        toast(when {
            !BuildConfig.AUTOTYPE -> "Copied: tap it in your keyboard's bar, or long-press → Paste"
            InsertService.isOn -> "No text box selected. Copied: long-press a text box → Paste"
            else -> "Copied: long-press a text box → Paste"
        })
    }

    private fun setState(s: BubbleView.State) {
        view?.state = s
        updateBubble()
        // A switch of "Type into apps" during a recording is applied once it's done.
        if (s == BubbleView.State.IDLE) reattach()
    }

    /**
     * Window y that puts the bubble right above the open keyboard (before your own lift),
     * or null when no keyboard is open or docking is off.
     */
    private fun dockedY(): Int? {
        if (!keyboardOpen) return null
        keyboardTop?.let { lastKbTop = it }
        // Keyboard height unknown for this reading: use the last one, or a typical keyboard.
        val top = keyboardTop ?: lastKbTop ?: (resources.displayMetrics.heightPixels * 0.58f).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()
        val atKeyboard = top - gap - size - screenOffset
        return if (prefs.dockAboveKeyboard) maxOf(0, atKeyboard)
        // Docking off: drawn on top of the keyboard it can stay put; otherwise move up only if it would be covered.
        else if (!onTopOfKeyboard && homeY > atKeyboard) maxOf(0, atKeyboard) else null
    }

    /** Bubble size in pixels from Settings (small / medium / large). */
    private fun wantedSize(): Int {
        val dpSize = when (prefs.bubbleSize) { "s" -> 58; "l" -> 88; else -> 72 }
        return (dpSize * resources.displayMetrics.density).toInt()
    }

    /** Picks up a size changed in Settings while the bubble is running. */
    private fun applySize() {
        val v = view ?: return
        val want = wantedSize()
        if (want == size) return
        size = want
        lp.width = want
        lp.height = want
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun measureOffset() {
        val v = view ?: return
        if (v.visibility != View.VISIBLE || !v.isAttachedToWindow) return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        screenOffset = loc[1] - lp.y
    }

    /** Shows the bubble only while typing (if that's on), and keeps it above the keyboard. */
    private fun updateBubble() {
        val v = view ?: return
        val busy = recorder.isRecording || working
        // Until a keyboard has been detected at least once, keep the bubble on screen:
        // if detection doesn't work on this phone, a hidden bubble would be unusable.
        val show = !prefs.onlyWithKeyboard || !prefs.keyboardSeen || keyboardOpen || busy
        val vis = if (show) View.VISIBLE else View.GONE
        if (v.visibility != vis) v.visibility = vis

        val dock = dockedY()
        val y = when {
            dock == null -> homeY
            prefs.dockAboveKeyboard -> (dock - prefs.dockLift).coerceIn(0, maxOf(0, resources.displayMetrics.heightPixels - size))
            else -> dock
        }
        if (!touchDragging && lp.y != y) {
            lp.y = y
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buzz(ms: Long) {
        if (!prefs.haptics) return
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        runCatching { vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    override fun onDestroy() {
        running = false
        instance = null
        drawnOverKeyboard = false
        keyboardNow = false
        main.removeCallbacksAndMessages(null)
        watcher?.stop()
        if (recorder.isRecording) recorder.cancel()
        if (working) Whisper.abort()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        // Free the model's memory (a few hundred MB) once the bubble is gone.
        Thread { Whisper.release() }.start()
        super.onDestroy()
    }
}

/** Whisper writes things like "[BLANK_AUDIO]" or "(music)" for non-speech; drop those. */
fun clean(s: String): String =
    s.replace(Regex("\\[[^\\]]*\\]|\\([^)]*(music|applause|silence|noise|laughs?)[^)]*\\)|♪+", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
