package com.voicebubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Keeps the floating microphone on screen.
 * Tap: start listening · tap again: stop and type it in · tap while it writes: cancel.
 * Hold 1 s (it buzzes): move it, and an X appears to turn it off. Pinch: make it bigger or smaller.
 * Saying "out of context, …" gives it a command instead of typing.
 */
class BubbleService : Service() {

    companion object {
        const val ACTION_STOP = "com.voicebubble.STOP"
        private const val CHANNEL = "bubble"
        private const val HOLD_MS = 900L
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
    @Volatile private var cancelled = false     // tapped again while it was writing the text out
    private var watcher: KeyboardWatcher? = null
    private var keyboardOpen = false
    private var keyboardTop: Int? = null
    private var homeY = 0          // where you put the bubble when no keyboard is open
    private var lastKbTop: Int? = null  // last known keyboard top, in case a reading lacks it
    private var touchDragging = false   // don't move the bubble under your finger
    private var screenOffset = 0   // window y -> screen y
    private var size = 0
    /** The text of the last dictation, for "rewrite". */
    private var lastText: String? = null

    // Small extra windows around the bubble.
    private var capsChip: TextView? = null
    private var capsLp: WindowManager.LayoutParams? = null
    private var closeChip: TextView? = null
    private var closeLp: WindowManager.LayoutParams? = null
    private var undoChip: TextView? = null
    private var undoLp: WindowManager.LayoutParams? = null
    private var replyPanel: View? = null
    private val hideClose = Runnable { closeChip?.visibility = View.GONE }
    private val hideUndo = Runnable { undoChip?.visibility = View.GONE }

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
        refreshLook()
        updateBubble()
        tileChanged()
        // Load the model now so the first transcription doesn't wait for it.
        if (prefs.engine == "live") Thread { Live.ensureLoaded(this, Live.byId(prefs.liveModelId)) }.start()
        Thread {
            val m = Models.byId(prefs.modelId)
            val path = Models.file(this, m).path
            Whisper.ensureLoaded(path)
            // Once per phone and model: find the fastest number of processor threads.
            val known = prefs.threadsFor(m.file)
            if (known > 0) Whisper.threads = known
            else if (Models.isDownloaded(this, m)) prefs.setThreadsFor(m.file, Whisper.benchmark(path))
        }.start()
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

    private fun tileChanged() {
        runCatching { TileService.requestListeningState(this, ComponentName(this, BubbleTile::class.java)) }
    }

    /** Skin and caps badge from the current settings. */
    private fun refreshLook() {
        view?.skin = Skins.byId(prefs.skin)
        view?.caps = prefs.capsLock
        view?.persona = prefs.persona != null
        styleCapsChip()
    }

    // ---------------- windows ----------------

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(w, h, lp.type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }

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
            // (No FLAG_SPLIT_TOUCH: a second finger outside the bubble still reaches it, for pinching.)
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
        addCapsChip()
        addCloseChip()
        addUndoChip()
        refreshLook()
    }

    private fun removeExtras() {
        listOf<View?>(capsChip, closeChip, undoChip, replyPanel).forEach { it?.let { x -> runCatching { wm.removeView(x) } } }
        capsChip = null; closeChip = null; undoChip = null; replyPanel = null
    }

    private fun chip(label: String, sizeDp: Int, desc: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        contentDescription = desc
        visibility = View.GONE
        setOnClickListener { onClick() }
    }

    private fun dpx(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** The ABC button next to the bubble while you speak: caps lock for dictation. */
    private fun addCapsChip() {
        val c = chip("ABC", 44, "Caps lock") {
            prefs.capsLock = !prefs.capsLock
            refreshLook()
            buzz(15)
        }
        val p = overlayParams(dpx(44), dpx(44))
        capsChip = c; capsLp = p
        styleCapsChip()
        runCatching { wm.addView(c, p) }.onFailure { capsChip = null; capsLp = null }
    }

    private fun styleCapsChip() {
        val chip = capsChip ?: return
        val on = prefs.capsLock
        chip.setTextColor(if (on) Color.rgb(58, 91, 217) else Color.WHITE)
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) Color.WHITE else Color.argb(220, 32, 38, 58))
            setStroke(dpx(2), Color.rgb(58, 91, 217))
        }
    }

    /** The X in the bubble's corner (after holding it): turns the bubble off. */
    private fun addCloseChip() {
        val c = chip("✕", 30, "Turn the bubble off") {
            toast("Bubble off. Turn it on in the app or from Quick Settings")
            stopSelf()
        }.apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(222, 48, 56)); setStroke(dpx(2), Color.WHITE) }
        }
        val p = overlayParams(dpx(30), dpx(30))
        closeChip = c; closeLp = p
        runCatching { wm.addView(c, p) }.onFailure { closeChip = null; closeLp = null }
    }

    /** "Undo" for a few seconds after it typed something (autotype version). */
    private fun addUndoChip() {
        val c = chip("Undo", 0, "Undo the last dictation") {
            if (InsertService.instance?.undo() == true) { buzz(15); toast("Undone") } else toast("Can't undo any more")
            undoChip?.visibility = View.GONE
        }.apply {
            setTextColor(Color.WHITE)
            setPadding(dpx(14), 0, dpx(14), 0)
            background = GradientDrawable().apply { cornerRadius = dpx(16).toFloat(); setColor(Color.argb(235, 32, 38, 58)) }
        }
        val p = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, dpx(32))
        undoChip = c; undoLp = p
        runCatching { wm.addView(c, p) }.onFailure { undoChip = null; undoLp = null }
    }

    private fun showUndo() {
        val c = undoChip ?: return
        c.visibility = View.VISIBLE
        positionChips()
        main.removeCallbacks(hideUndo)
        main.postDelayed(hideUndo, 5000)
    }

    /** Keeps the little buttons next to the bubble. */
    private fun positionChips() {
        val bubble = view ?: return
        val visible = bubble.visibility == View.VISIBLE
        val dm = resources.displayMetrics
        val gap = dpx(8)
        val onRight = lp.x + size / 2 > dm.widthPixels / 2

        capsChip?.let { c ->
            val p = capsLp ?: return@let
            val show = recorder.isRecording && visible
            c.visibility = if (show) View.VISIBLE else View.GONE
            if (show) {
                p.x = if (onRight) lp.x - p.width - gap else lp.x + size + gap
                p.y = lp.y + (size - p.height) / 2
                runCatching { wm.updateViewLayout(c, p) }
            }
        }
        closeChip?.let { c ->
            val p = closeLp ?: return@let
            if (!visible) c.visibility = View.GONE
            p.x = lp.x + size - p.width + dpx(2)
            p.y = lp.y - dpx(2)
            runCatching { wm.updateViewLayout(c, p) }
        }
        undoChip?.let { c ->
            val p = undoLp ?: return@let
            if (!visible) c.visibility = View.GONE
            val w = if (c.width > 0) c.width else dpx(64)
            p.x = (lp.x + size / 2 - w / 2).coerceIn(0, maxOf(0, dm.widthPixels - w))
            p.y = if (lp.y > dpx(60)) lp.y - p.height - dpx(4) else lp.y + size + dpx(4)
            runCatching { wm.updateViewLayout(c, p) }
        }
        positionLive()
    }

    /** Rotated: use the position you chose for this orientation (portrait and landscape are separate). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        main.postDelayed({
            val v = view ?: return@postDelayed
            val dm = resources.displayMetrics
            lp.x = (prefs.bubbleX.takeIf { it >= 0 } ?: (dm.widthPixels - size)).coerceIn(0, maxOf(0, dm.widthPixels - size))
            homeY = (prefs.bubbleY.takeIf { it >= 0 } ?: (dm.heightPixels / 3)).coerceIn(0, maxOf(0, dm.heightPixels - size))
            lp.y = homeY
            lastKbTop = null
            runCatching { wm.updateViewLayout(v, lp) }
            updateBubble()
        }, 150)
    }

    /** Called by the accessibility service when windows change (keyboard opened/closed). */
    fun keyboardMayHaveChanged() = watcher?.poke()

    /** Re-creates the bubble when "Type into apps" is switched on or off, so it moves over or under the keyboard. */
    fun reattach() {
        main.post {
            if (!running || recorder.isRecording || working) return@post
            val old = view ?: return@post
            val wantOnTop = InsertService.instance != null
            if (wantOnTop == onTopOfKeyboard) return@post
            runCatching { wm.removeView(old) }
            removeExtras()
            view = null
            addBubble()
            updateBubble()
        }
    }

    // ---------------- touch: tap, hold to move, pinch to resize ----------------

    private fun attachTouch(v: BubbleView) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var moveMode = false; var held = false; var slid = false; var pinched = false
        val hold = Runnable {
            held = true
            when {
                recorder.isRecording -> cancelRecording()
                working -> cancelWork()
                else -> {
                    // Held for a second: it buzzes, can be moved, and shows the X to turn it off.
                    moveMode = true
                    touchDragging = true
                    v.moving = true
                    buzz(40)
                    main.removeCallbacks(hideClose)
                    closeChip?.visibility = View.VISIBLE
                    positionChips()
                }
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    moveMode = false; held = false; slid = false; pinched = false
                    main.postDelayed(hold, HOLD_MS)
                }
                MotionEvent.ACTION_POINTER_DOWN -> main.removeCallbacks(hold)
                MotionEvent.ACTION_MOVE -> {
                    if (pinched || e.pointerCount > 1) return@setOnTouchListener true
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (moveMode) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, lp) }
                        positionChips()
                    } else if (!slid && (abs(dx) > slop || abs(dy) > slop)) {
                        // Moved before the hold finished: not a tap, and not a move (hold first to move).
                        slid = true
                        main.removeCallbacks(hold)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(hold)
                    v.moving = false
                    touchDragging = false
                    if (moveMode) {
                        savePosition(v)
                        // The X stays a moment so you can tap it.
                        main.postDelayed(hideClose, 3000)
                    } else if (!held && !slid && !pinched) onTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(hold); v.moving = false; touchDragging = false; updateBubble()
                }
            }
            true
        }
    }

    private fun savePosition(v: BubbleView) {
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
        positionChips()
    }

    /** Resizes around the bubble's centre (pinching). */
    private fun resizeTo(px: Int) {
        val v = view ?: return
        if (px == size) return
        val delta = (size - px) / 2
        size = px
        lp.width = px; lp.height = px
        lp.x = (lp.x + delta).coerceAtLeast(0)
        lp.y = (lp.y + delta).coerceAtLeast(0)
        homeY = lp.y
        runCatching { wm.updateViewLayout(v, lp) }
        positionChips()
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
            // While it's working, a tap does nothing (no accidental cancels); hold the bubble to cancel.
            working -> toast("Still working… hold the bubble to cancel")
            recorder.isRecording -> finishRecording()
            else -> startRecording()
        }
    }

    private fun cancelRecording() {
        if (session?.typed == true) InsertService.instance?.undo()
        main.removeCallbacks(chunker)
        endSession()
        Whisper.abort()
        recorder.cancel()
        setState(BubbleView.State.IDLE)
        buzz(60)
        toast("Cancelled")
    }

    /** Stops turning speech into text: nothing gets typed. */
    private fun cancelWork() {
        if (cancelled) return
        cancelled = true
        if (session?.typed == true) InsertService.instance?.undo()
        endSession()          // drops the dictation still being worked on
        Whisper.abort()
        working = false
        setState(BubbleView.State.IDLE)
        buzz(60)
        toast("Cancelled")
    }

    // ---------------- dictation ----------------

    /** Everything about one dictation, worked out when you start talking. */
    private class Session(
        val token: Int, val app: String?, val cat: Styles.Category, val style: Styles.Style,
        val modelPath: String, val lang: String, val extra: Boolean, val prompt: String,
        val toEnglish: Boolean, val aiTarget: String?, val word: String,
    ) {
        /** Set when the Live (streaming) engine is used: text grows while you talk. */
        var live: Live.Session? = null
        var typed = false                   // Live + autotype: words already typed into the text box
        var limit: Int? = null              // longest message for this app (null = none)
        var fed = 0                         // Live: samples already given to it
        var cutAt = 0                       // audio before this sample is already being turned into text
        val parts = ArrayList<String?>()    // text of each part (null = still working on it)
        var failed = false
    }

    private var session: Session? = null
    @Volatile private var sessionToken = 0
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var liveView: TextView? = null
    private var offerView: TextView? = null

    private fun startRecording() {
        var chosen = Models.byId(prefs.modelId)
        if (!Setup.modelReady(this)) {
            toast("Open Voice Bubble and download the speech model first")
            return
        }
        hideReplies()
        // The app you're typing in decides the style and (if you set one) the language.
        val app = AppDetector.current(this)
        val appLang = Styles.langFor(this, app)
        var lang = appLang ?: if (chosen.englishOnly) "en" else prefs.lang
        if (lang != "en" && chosen.englishOnly) {
            // English-only model, but this app is set to another language: use an all-languages model if you have one.
            val alt = Models.all.firstOrNull { !it.englishOnly && Models.isDownloaded(this, it) }
            if (alt != null) chosen = alt
            else { toast("Download an \"all languages\" model to dictate in that language here"); lang = "en" }
        }
        // Translation: into English, Whisper does it on the phone; anything else goes through the AI key.
        val target = prefs.translateTo
        val toEnglish = target.equals("English", true) && !chosen.englishOnly
        val aiTarget = target?.takeIf { !toEnglish && !(it.equals("English", true) && !Ai.hasKey(this)) }
        val s = Session(++sessionToken, app, Styles.categoryOf(app), Styles.forApp(this, app),
            Models.file(this, chosen).path, lang, prefs.extraAccuracy,
            // Your command word first, so Whisper writes it right; then your words and style.
            listOf(prefs.commandWord + ".", Words.prompt(this), Adapt.prompt(this)).filter { it.isNotBlank() }.joinToString(" "),
            toEnglish, aiTarget, prefs.commandWord)
        // Live engine: English, no on-phone translation (Whisper does that), model downloaded and loaded.
        // Live engine: English (anything unless this app is set to another language), no on-phone translation.
        if (prefs.engine == "live" && (appLang == null || appLang == "en") && !toEnglish) {
            val lm = Live.byId(prefs.liveModelId)
            when {
                !Live.isDownloaded(this, lm) -> toast("Live model not downloaded: using Whisper")
                Live.ensureLoaded(this, lm) -> s.live = Live.start()
                else -> toast("Live engine couldn't start (${Live.lastError ?: "unknown"}): using Whisper")
            }
        }
        s.limit = Limit.forApp(this, app)
        try {
            recorder.onMaxReached = { main.post { if (recorder.isRecording) finishRecording() } }
            recorder.start()
        } catch (e: Exception) {
            s.live?.cancel()
            toast("Can't use the microphone. Open Voice Bubble and press Start again")
            return
        }
        session = s
        buzz(30)
        Sounds.start(prefs.sound)
        setState(BubbleView.State.RECORDING)
        main.post(ticker)
        main.postDelayed(chunker, if (s.live != null) 150 else 1000)
    }

    /**
     * While you talk: at each pause, the part said so far goes off to be turned into text, so when you
     * tap stop only the last bit is left. Very long stretches without a pause are cut at the quietest moment.
     */
    private val chunker = object : Runnable {
        override fun run() {
            val s = session ?: return
            if (!recorder.isRecording) return
            s.live?.let { live ->
                // Live: hand over the new audio; the words appear as it goes.
                val total = recorder.sampleCount
                if (total > s.fed) {
                    val chunk = recorder.copy(s.fed, total)
                    s.fed = total
                    worker.execute {
                        if (s.token != sessionToken) return@execute
                        val text = live.feed(chunk)
                        main.post { if (s.token == sessionToken) typeLive(s, text) }
                    }
                }
                main.postDelayed(this, 150)
                return
            }
            val total = recorder.sampleCount
            if (total - s.cutAt > Recorder.RATE * 5 / 2) {
                val seg = recorder.copy(s.cutAt, total)
                var cut = Audio.findCut(seg, minSpeechSec = 2.0, minPauseSec = 0.3)
                if (cut < 0 && seg.size > Recorder.RATE * 26) cut = quietestCut(seg)
                if (cut > 0) {
                    s.cutAt += cut
                    transcribePart(s, seg.copyOf(cut))
                }
            }
            main.postDelayed(this, 400)
        }
    }

    /** The quietest 100 ms between 15 and 26 seconds in. */
    private fun quietestCut(seg: FloatArray): Int {
        val rms = Audio.frameRms(seg)
        val from = 150.coerceAtMost(rms.size - 1)
        val to = 260.coerceAtMost(rms.size - 1)
        var best = from
        for (f in from..to) if (rms[f] < rms[best]) best = f
        return best * Audio.FRAME + Audio.FRAME / 2
    }

    /** Turns one part into text in the background (parts run one after another, in order). */
    private fun transcribePart(s: Session, audio: FloatArray) {
        val idx = synchronized(s) { s.parts.add(null); s.parts.size - 1 }
        worker.execute {
            if (s.token != sessionToken) return@execute
            val trimmed = Audio.trimSilence(audio)
            // The part before helps Whisper carry on the sentence correctly.
            val prev = synchronized(s) { s.parts.take(idx).filterNotNull().lastOrNull().orEmpty() }.takeLast(200)
            val txt = if (hasSpeech(trimmed)) runCatching {
                Whisper.transcribe(s.modelPath, trimmed, s.lang, (s.prompt + " " + prev).trim(), s.extra, s.toEnglish)
            }.getOrNull() else ""
            synchronized(s) {
                if (txt == null) s.failed = true
                s.parts[idx] = txt?.let { clean(it) } ?: ""
            }
            main.post { if (s.token == sessionToken) showLive(s) }
        }
    }

    private fun finishRecording() {
        val s = session
        main.removeCallbacks(chunker)
        val audio = recorder.stop()
        buzz(30)
        Sounds.stop(prefs.sound)
        if (s == null || !hasSpeech(audio)) {
            endSession()
            setState(BubbleView.State.IDLE)
            toast("Didn't hear anything")
            return
        }
        cancelled = false
        working = true
        setState(BubbleView.State.WORKING)
        val caps = prefs.capsLock
        val seconds = audio.size / Recorder.RATE.toFloat()
        val live = s.live
        if (live == null && s.cutAt < audio.size) transcribePart(s, audio.copyOfRange(s.cutAt, audio.size))
        // Runs after every part is done (one worker, in order).
        worker.execute {
            if (s.token != sessionToken) return@execute
            val said: String?
            val failed: Boolean
            if (live != null) {
                // Live: most of it is already done; add the last bit and finish.
                if (audio.size > s.fed) live.feed(audio.copyOfRange(s.fed, audio.size))
                said = clean(live.finish()).takeIf { it.isNotEmpty() }
                failed = false
            } else {
                val parts = synchronized(s) { s.parts.map { it.orEmpty() } }
                said = parts.filter { it.isNotBlank() }.joinToString(" ").trim().takeIf { it.isNotEmpty() }
                failed = said == null && synchronized(s) { s.failed }
            }
            if (said != null) Adapt.observe(this, said)
            // Just "scratch that" on its own: take back the last dictation.
            val split = said?.let { if (Commands.isScratch(it)) Commands.Split("", Commands.Cmd.Undo) else Commands.split(it, s.word) }
            if (split != null) {
                // Text said before the command gets the usual treatment first.
                val before = split.before.takeIf { it.isNotEmpty() }?.let { process(it, s.style, s.cat) }
                main.post {
                    if (s.token != sessionToken) return@post
                    endSession()
                    working = false
                    setState(BubbleView.State.IDLE)
                    val cmd = split.cmd
                    if (s.typed) InsertService.instance?.undo()   // the command words were typed while talking
                    when {
                        before == null -> runCommand(cmd)
                        // "…text…, out of context, make this longer": rewrite what you just said, then type it.
                        cmd is Commands.Cmd.Rewrite -> rewrite(cmd.instruction, before, s.limit)
                        else -> { deliver(before, s.limit); Stats.record(this, before, seconds, s.app); runCommand(cmd) }
                    }
                }
                return@execute
            }
            var text = said?.let { process(it, s.style, s.cat) }
            var problem: String? = null
            // "Talk like …" mode: every dictation rewritten in that voice.
            val persona = prefs.persona
            if (text != null && Shakespeare.wanted(persona) && (prefs.instantShakespeare || !Ai.hasKey(this))) {
                text = Shakespeare.apply(text)   // instant, no AI
            } else if (text != null && persona != null && Ai.hasKey(this)) {
                text = try { Ai.rewrite(this, text, "Make me sound like $persona", s.limit) } catch (e: Ai.Failure) { problem = e.message; text }
            }
            if (text != null && s.aiTarget != null) {
                text = try { Ai.translate(this, text, s.aiTarget, s.limit) } catch (e: Ai.Failure) { problem = e.message; text }
            }
            if (caps) text = text?.uppercase()
            main.post {
                if (s.token != sessionToken) return@post
                endSession()
                working = false
                setState(BubbleView.State.IDLE)
                problem?.let { toast("AI step skipped: $it") }
                when {
                    failed -> toast("Couldn't turn that into text")
                    text.isNullOrEmpty() -> toast("Didn't catch any words")
                    else -> {
                        deliverFinal(s, text!!)
                        Stats.record(this, text!!, seconds, s.app)
                    }
                }
            }
        }
    }

    /** Forget the current dictation: anything still being worked on is dropped. */
    private fun endSession() {
        session?.live?.let { l -> worker.execute { l.cancel() } }
        session = null
        sessionToken++
        hideLive()
    }

    // ----- live text next to the bubble -----

    /** Live engine: put the words so far straight into the text box (autotype), or in the live box. */
    private fun typeLive(s: Session, text: String) {
        val svc = InsertService.instance
        if (!prefs.typeWhileTalking || svc == null) { showLiveText(text); return }
        if (text.isBlank()) return
        if (!s.typed) {
            if (svc.insert(text)) s.typed = true else showLiveText(text)
        } else if (!svc.replaceLast(text)) {
            showLiveText(text)   // you moved the cursor or edited: show it here instead
        }
    }

    /** The finished text: swaps out what was typed while you talked, or types it now. */
    private fun deliverFinal(s: Session, text: String) {
        if (s.typed) {
            val (first, rest) = fitToLimit(text, s.limit)
            if (InsertService.instance?.replaceLast(first) == true) {
                lastText = first; buzz(15); showUndo()
                rest?.let { copyRest(it, s.limit) }
                return
            }
        }
        deliver(text, s.limit)
    }

    private fun fitToLimit(text: String, limit: Int?): Pair<String, String?> =
        if (limit == null) text to null else Limit.fit(text, limit)

    /** Too long for one message: the rest goes on the clipboard for the next one. */
    private fun copyRest(rest: String, limit: Int?) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", rest))
        toast("Longer than $limit characters: the rest is copied for your next message")
    }

    private fun showLive(s: Session) =
        showLiveText(synchronized(s) { s.parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ") })

    private fun showLiveText(text: String) {
        if (!prefs.liveText) return
        if (text.isBlank()) return
        val v = liveView ?: TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.START
            setPadding(dpx(14), dpx(10), dpx(14), dpx(10))
            background = GradientDrawable().apply { cornerRadius = dpx(16).toFloat(); setColor(Color.argb(235, 26, 31, 51)) }
        }.also { tv ->
            val dm = resources.displayMetrics
            val p = overlayParams(minOf(dm.widthPixels - dpx(24), dpx(340)), WindowManager.LayoutParams.WRAP_CONTENT)
            runCatching { wm.addView(tv, p) }.onSuccess { liveView = tv }
        }
        v.text = text + if (recorder.isRecording) " …" else ""
        positionLive()
    }

    private fun positionLive() {
        val v = liveView ?: return
        val p = v.layoutParams as? WindowManager.LayoutParams ?: return
        val dm = resources.displayMetrics
        p.x = (lp.x + size / 2 - p.width / 2).coerceIn(dpx(12), maxOf(dpx(12), dm.widthPixels - p.width - dpx(12)))
        val h = if (v.height > 0) v.height else dpx(70)
        p.y = if (lp.y > h + dpx(16)) lp.y - h - dpx(10) else lp.y + size + dpx(10)
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun hideLive() {
        liveView?.let { runCatching { wm.removeView(it) } }
        liveView = null
    }

    // ----- "add this word to your dictionary?" after you correct one -----

    /** Called by the typing service when you fixed words it typed ([heard] → [meant]). */
    fun offerWord(heard: String, meant: String) {
        main.post {
            offerView?.let { runCatching { wm.removeView(it) } }
            val inDict = Words.dictionary(this).any { it == meant }
            val v = TextView(this).apply {
                text = if (inDict) "Remember \u201c$heard\u201d → \u201c$meant\u201d?  ✓" else "Add \u201c$meant\u201d to your dictionary?  ✓"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpx(16), dpx(10), dpx(16), dpx(10))
                background = GradientDrawable().apply { cornerRadius = dpx(18).toFloat(); setColor(Color.rgb(58, 91, 217)) }
                setOnClickListener {
                    // The word goes in your dictionary, and this exact mishearing is fixed from now on.
                    if (!inDict && meant.split(' ').size <= 2) Words.saveDictionary(this@BubbleService, Words.dictionary(this@BubbleService) + meant)
                    Adapt.addFix(this@BubbleService, heard, meant)
                    toast("Got it: \u201c$heard\u201d will be \u201c$meant\u201d ✓")
                    buzz(15)
                    runCatching { wm.removeView(this) }
                    offerView = null
                }
            }
            val dm = resources.displayMetrics
            val p = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                x = dpx(16)
                y = maxOf(dpx(40), lp.y - dpx(56))
            }
            if (runCatching { wm.addView(v, p) }.isSuccess) {
                offerView = v
                main.postDelayed({
                    if (offerView == v) { runCatching { wm.removeView(v) }; offerView = null }
                }, 8000)
            }
            if (dm.widthPixels <= 0) return@post
        }
    }

    /** Voice commands, emoji, fillers, numbers → style → your dictionary and shortcuts. */
    private fun process(said: String, style: Styles.Style, cat: Styles.Category): String {
        var t = said
        if (Features.isOn(this, Commands.Feature.COMMANDS)) t = TextTools.voiceCommands(t)
        if (Features.isOn(this, Commands.Feature.EMOJI)) t = TextTools.emoji(t)
        if (Features.isOn(this, Commands.Feature.FILLER)) t = TextTools.removeFillers(t)
        if (Features.isOn(this, Commands.Feature.NUMBERS)) t = TextTools.numbersAndDates(t)
        t = Styles.apply(t, style, cat)
        return Words.apply(this, t, capitalize = style != Styles.Style.VERY_CASUAL && style != Styles.Style.GEN_Z)
    }

    /** Types it into the selected text box; otherwise puts it on the clipboard. */
    private fun deliver(full: String, limit: Int? = null) {
        val (text, rest) = fitToLimit(full, limit)
        lastText = text
        val typed = InsertService.instance?.let { runCatching { it.insert(text) }.getOrDefault(false) } ?: false
        if (typed) {
            buzz(15)
            showUndo()
            // Typed the part that fits; the rest goes on the clipboard for the next message.
            rest?.let { copyRest(it, limit) }
            return
        }
        copyOut(text)
        if (rest != null) {
            toast("First $limit characters copied. The rest is in Recent on the Home screen")
            return
        }
        toast(when {
            !BuildConfig.AUTOTYPE -> "Copied: tap it in your keyboard's bar, or long-press → Paste"
            InsertService.isOn -> "No text box selected. Copied: long-press a text box → Paste"
            else -> "Copied: long-press a text box → Paste"
        })
    }

    private fun copyOut(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Voice Bubble", text))
        buzz(15)
    }

    // ---------------- "out of context, …" commands ----------------

    private fun runCommand(cmd: Commands.Cmd) {
        buzz(20)
        when (cmd) {
            is Commands.Cmd.Toggle -> {
                Features.set(this, cmd.feature, cmd.on)
                refreshLook()
                updateBubble()
                toast("${cmd.feature.title} ${if (cmd.on) "on ✓" else "off"}")
            }
            is Commands.Cmd.Translate -> {
                val lang = cmd.language?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
                prefs.translateTo = lang
                toast(when {
                    lang == null -> "Translation off"
                    lang.equals("English", true) -> "Translating everything into English"
                    !Ai.hasKey(this) -> "Translating into $lang needs an AI key (Settings → AI)"
                    else -> "Translating everything into $lang"
                })
            }
            Commands.Cmd.Undo -> {
                if (InsertService.instance?.undo() == true) toast("Undone")
                else toast(if (BuildConfig.AUTOTYPE) "Nothing to undo" else "Undo needs the autotype version")
            }
            is Commands.Cmd.Rewrite -> rewrite(cmd.instruction)
            is Commands.Cmd.Reply -> reply(cmd.instruction)
            is Commands.Cmd.Summarize -> summarize(cmd.instruction)
            is Commands.Cmd.ShakespeareMode -> {
                prefs.persona = "Shakespeare"
                prefs.instantShakespeare = cmd.instant
                refreshLook()
                toast(when {
                    cmd.instant -> "Instant Shakespeare on: no waiting"
                    !Ai.hasKey(this) -> "AI Shakespeare needs an AI key, so it's the instant one for now"
                    else -> "AI Shakespeare on"
                })
            }
            is Commands.Cmd.Persona -> {
                prefs.persona = cmd.who
                refreshLook()
                toast(when {
                    cmd.who == null -> "Back to your normal voice"
                    !Ai.hasKey(this) -> "Talking like ${cmd.who} needs an AI key (Settings → AI)"
                    else -> "Talking like ${cmd.who} until you say \"${prefs.commandWord}, talk normally\""
                })
            }
            is Commands.Cmd.Unknown -> toast("Didn't get that command: \"${cmd.said.take(40)}\"")
        }
    }

    /** "rewrite, make this more polite": the AI rewrites your last dictation and swaps it in. */
    /**
     * "rewrite, make this more polite". With [fresh] text (said in the same breath) it rewrites that and
     * types the result; otherwise it rewrites your last dictation and swaps it in.
     */
    private fun rewrite(instruction: String, fresh: String? = null, limit: Int? = Limit.forApp(this, AppDetector.current(this))) {
        val text = fresh ?: lastText ?: run { toast("Dictate something first, then ask to rewrite it"); return }
        // Shakespeare can be done instantly on the phone.
        if (Shakespeare.wanted(instruction) && (prefs.instantShakespeare || !Ai.hasKey(this))) {
            val out = Shakespeare.apply(text)
            if (fresh != null) deliver(out)
            else if (InsertService.instance?.replaceLast(out) == true) { lastText = out; buzz(15); showUndo() }
            else { lastText = out; copyOut(out); toast("Copied: paste it over the old one") }
            return
        }
        if (!Ai.hasKey(this)) {
            toast("Rewriting needs an AI key (Settings → AI)")
            fresh?.let { deliver(it) }   // don't lose what you said
            return
        }
        aiJob({ Ai.rewrite(this, text, instruction, limit) }, onFail = { fresh?.let { deliver(it, limit) } }) { out ->
            if (fresh != null) { deliver(out, limit); return@aiJob }
            lastText = out
            if (InsertService.instance?.replaceLast(out) == true) { buzz(15); toast("Rewritten ✓"); showUndo() }
            else { copyOut(out); toast("Rewritten text copied: paste it over the old one") }
        }
    }

    /** "reply": reads the chat on screen and offers 3 replies to tap. */
    private fun reply(instruction: String) {
        val svc = InsertService.instance ?: run {
            toast(if (BuildConfig.AUTOTYPE) "Reply suggestions need \"Type into apps\" on" else "Reply suggestions need the autotype version")
            return
        }
        if (!Ai.hasKey(this)) { toast("Reply suggestions need an AI key (Settings → AI)"); return }
        val screen = svc.screenText()
        if (screen.isBlank()) { toast("Couldn't read this screen"); return }
        aiJob({ Ai.replies(this, screen, instruction).joinToString("\n") }) { out ->
            val options = out.lines().filter { it.isNotBlank() }
            if (options.isEmpty()) toast("No suggestions this time") else showReplies(options)
        }
    }

    /** "summarise": a short summary of the chat on screen, with a Copy button. */
    private fun summarize(instruction: String) {
        val svc = InsertService.instance ?: run {
            toast(if (BuildConfig.AUTOTYPE) "Summaries need \"Type into apps\" on" else "Summaries need the autotype version")
            return
        }
        if (!Ai.hasKey(this)) { toast("Summaries need an AI key (Settings → AI)"); return }
        val screen = svc.screenText()
        if (screen.isBlank()) { toast("Couldn't read this screen"); return }
        aiJob({ Ai.summary(this, screen, instruction) }) { out -> showSummary(out) }
    }

    private fun showSummary(text: String) {
        hideReplies()
        val dm = resources.displayMetrics
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpx(14), dpx(12), dpx(14), dpx(12))
            background = GradientDrawable().apply { cornerRadius = dpx(20).toFloat(); setColor(Color.argb(245, 26, 31, 51)) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            this.text = "Summary"; setTextColor(Color.rgb(169, 187, 255)); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(this).apply {
            this.text = "Copy"; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dpx(12), dpx(4), dpx(12), dpx(4))
            setOnClickListener { copyOut(text); toast("Copied"); hideReplies() }
        })
        head.addView(TextView(this).apply {
            this.text = "✕"; setTextColor(Color.WHITE); textSize = 16f; setPadding(dpx(12), 0, dpx(4), 0)
            setOnClickListener { hideReplies() }
        })
        panel.addView(head)
        panel.addView(TextView(this).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 15f; setPadding(0, dpx(8), 0, 0)
            setLineSpacing(0f, 1.15f)
        })
        val p = overlayParams(dm.widthPixels - dpx(24), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dpx(12); y = dpx(60)
        }
        replyPanel = panel
        runCatching { wm.addView(panel, p) }.onFailure { replyPanel = null }
    }

    /** Runs an AI request with the bubble spinning; tap the bubble to cancel. */
    private fun aiJob(work: () -> String, onFail: (() -> Unit)? = null, done: (String) -> Unit) {
        cancelled = false
        working = true
        setState(BubbleView.State.WORKING)
        Thread {
            val result = try { Result.success(work()) } catch (e: Ai.Failure) { Result.failure(e) }
            main.post {
                working = false
                setState(BubbleView.State.IDLE)
                if (cancelled) { cancelled = false; return@post }
                result.onSuccess(done).onFailure { toast(it.message ?: "Something went wrong"); onFail?.invoke() }
            }
        }.start()
    }

    private fun showReplies(options: List<String>) {
        hideReplies()
        val dm = resources.displayMetrics
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpx(10), dpx(10), dpx(10), dpx(10))
            background = GradientDrawable().apply { cornerRadius = dpx(20).toFloat(); setColor(Color.argb(245, 26, 31, 51)) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "Tap a reply"; setTextColor(Color.rgb(169, 187, 255)); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dpx(6), 0, 0, dpx(6))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(this).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 16f; setPadding(dpx(12), 0, dpx(6), dpx(6))
            setOnClickListener { hideReplies() }
        })
        panel.addView(head)
        options.forEach { o ->
            panel.addView(TextView(this).apply {
                text = o
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(dpx(14), dpx(11), dpx(14), dpx(11))
                background = GradientDrawable().apply { cornerRadius = dpx(14).toFloat(); setColor(Color.rgb(58, 91, 217)) }
                setOnClickListener {
                    hideReplies()
                    deliver(o)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpx(6) })
        }
        val p = overlayParams(dm.widthPixels - dpx(24), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dpx(12)
            // Just above the keyboard (or the bubble), so the chat stays visible.
            val bottom = (keyboardTop ?: lastKbTop ?: (lp.y + screenOffset)) - dpx(12) - screenOffset
            y = maxOf(dpx(40), bottom - dpx(70) * (options.size + 1))
        }
        replyPanel = panel
        runCatching { wm.addView(panel, p) }.onFailure { replyPanel = null }
    }

    private fun hideReplies() {
        replyPanel?.let { runCatching { wm.removeView(it) } }
        replyPanel = null
    }

    // ---------------- bubble placement ----------------

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
        val gap = dpx(8)
        val atKeyboard = top - gap - size - screenOffset
        return if (prefs.dockAboveKeyboard) maxOf(0, atKeyboard)
        // Docking off: drawn on top of the keyboard it can stay put; otherwise move up only if it would be covered.
        else if (!onTopOfKeyboard && homeY > atKeyboard) maxOf(0, atKeyboard) else null
    }

    /** Bubble size in pixels (pinch it, or Settings → Bubble size). */
    private fun wantedSize(): Int = dpx(prefs.bubbleSizeDp)

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
        // Only a recording keeps it on screen without a keyboard: AI and text work finish in the background.
        val busy = recorder.isRecording
        // Hide only when keyboard detection is confirmed to work on this phone (the accessibility
        // service sees the keyboard directly; otherwise Home verifies it). Otherwise always show.
        val verified = InsertService.isOn || (prefs.keyboardVerified && !prefs.keyboardBroken)
        val show = !prefs.onlyWithKeyboard || !verified || keyboardOpen || busy
        val vis = if (show) View.VISIBLE else View.GONE
        if (v.visibility != vis) v.visibility = vis
        liveView?.visibility = vis

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
        positionChips()
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
        removeExtras()
        hideLive()
        offerView?.let { runCatching { wm.removeView(it) } }
        offerView = null
        worker.shutdownNow()
        view = null
        tileChanged()
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
