# Voice Bubble

A floating microphone that sits on top of every Android app. Tap it, talk, tap again, and your
words are typed into the text box you're in. Speech recognition is Whisper (via
[whisper.cpp](https://github.com/ggml-org/whisper.cpp)) running **on the phone**: free, no account,
no subscription, and your voice never leaves the device. Internet is only used once, to download
the speech model.

## Install
GitHub builds the APK automatically whenever something in `voice-bubble/` changes
(workflow: `.github/workflows/build-voice-bubble.yml`). On your phone, open the repo's
**Releases** page, find the newest **Voice Bubble build**, and tap `voice-bubble.apk`.
Allow "install unknown apps" for your browser when Android asks.

There are two versions (same app, install one):
- `voice-bubble.apk`: copies your words; tap them in the keyboard's suggestion bar or paste.
- `voice-bubble-autotype.apk`: also types the words in for you, using an accessibility service.
  Play Protect blocks browser-installed apps that have one, so this version only installs via
  `adb install` from a computer, or with Play Protect's app scanning briefly turned off.

## First run
1. **Speech model:** pick *Accurate* (190 MB, recommended) or *Fast* (60 MB) and press Download. One time, Wi-Fi recommended.
2. **Language:** pick the language you speak (or Auto-detect).
3. **Permissions:**
   - Microphone: required.
   - Show over other apps: required for the bubble.
   - Type into apps (autotype version only): Accessibility → Installed apps → Voice Bubble → On.
4. Press **Show floating microphone**.

## Using it
- **Tap** the bubble: it turns red and listens (the ring follows your voice, the timer shows the length).
- **Tap again**: it turns blue while it writes out what you said, then types it at your cursor.
- **Long-press**: cancel.
- **Drag**: move it. Hide it from its notification.

Keep using Gboard (or any keyboard) as normal; the bubble works next to it.

## Notes
- Built for 64-bit ARM phones from roughly 2018 onwards (ARMv8.2).
- A sentence takes a few seconds to write out on *Accurate*, less on *Fast*.
- Recordings stop automatically after 5 minutes.
