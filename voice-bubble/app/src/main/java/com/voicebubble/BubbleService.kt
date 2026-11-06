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
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var view: BubbleView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val recorder = Recorder()
    @Volatile private var working = false
    private var watcher: KeyboardWatcher? = null
    private var keyboardOpen = false
    private var keyboardTop: Int? = null
    private var homeY = 0          // where you put the bubble; it moves up only while a keyboard covers it
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
        prefs = Prefs(this)
        if (view == null) {
            addBubble()
            watcher = KeyboardWatcher(this, wm) { open, top ->
                keyboardOpen = open
                keyboardTop = top
                updateBubble()
            }.also { it.start() }
        }
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
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        size = (72 * dm.density).toInt()
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        wm.addView(v, lp)
        view = v
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
                    dragging = false; longPressed = false
                    main.postDelayed(longPress, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) { dragging = true; main.removeCallbacks(longPress) }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    if (dragging) { prefs.bubbleX = lp.x; prefs.bubbleY = lp.y; homeY = lp.y }
                    else if (!longPressed) onTap()
                }
                MotionEvent.ACTION_CANCEL -> main.removeCallbacks(longPress)
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
        Thread {
            val raw = runCatching { Whisper.transcribe(model, audio, lang, prompt) }.getOrNull()
            // Your dictionary spellings, then your shortcuts.
            val text = raw?.let { clean(it) }?.takeIf { it.isNotEmpty() }?.let { Words.apply(this, it) }
            main.post {
                working = false
                setState(BubbleView.State.IDLE)
                when {
                    raw == null -> toast("Couldn't turn that into text")
                    text.isNullOrEmpty() -> toast("Didn't catch any words")
                    else -> deliver(text)
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
        val show = !prefs.onlyWithKeyboard || keyboardOpen || busy
        val vis = if (show) View.VISIBLE else View.GONE
        if (v.visibility != vis) v.visibility = vis

        var y = homeY
        val top = keyboardTop
        if (keyboardOpen && top != null) {
            val margin = (12 * resources.displayMetrics.density).toInt()
            val maxY = top - margin - size - screenOffset
            if (y > maxY) y = maxOf(0, maxY)
        }
        if (lp.y != y) {
            lp.y = y
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buzz(ms: Long) {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        runCatching { vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    override fun onDestroy() {
        running = false
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
