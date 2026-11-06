# Voice Bubble

A floating microphone that sits on top of every Android app. Tap it, talk, tap again, and your
words are typed into the text box you're in. Speech recognition is Whisper (via
[whisper.cpp](https://github.com/ggml-org/whisper.cpp)) running **on the phone**: free, no account,
no subscription, and your voice never leaves the device. Internet is only used once, to download
the speech model.

## The app
Same design language as Comboed (light/dark, rounded cards, bottom tabs):
- **Home:** the floating-microphone switch, a setup checklist, your numbers (words today, time
  saved, day streak), a box to try it, and recent dictations (tap to copy, hold to delete).
- **Dictionary**, **Shortcuts** and **Style** tabs, each with search/add/edit and import.
- **Settings** (gear): speech model, language, bubble (only while typing, size, vibration,
  position), permissions, theme (same as phone / light / dark), privacy (history), updates.

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
- By default it only appears while a keyboard is open (any keyboard), and moves up if the keyboard
  would cover it. Untick "Only show while a keyboard is open" to keep it on screen all the time.

Keep using Gboard (or any keyboard) as normal; the bubble works next to it.

## Updating
The app checks GitHub for a newer build when you open it and shows an **Update** card. Tap it,
open the download, tap **Update**. Every build is signed with the same key, so it installs over
the old one: no uninstalling, and the model, words and settings stay.

## Dictionary & shortcuts
- **Dictionary:** names and words it should spell your way. They are given to Whisper up front,
  and anything that sounds close ("Komboed", "combo ed") is rewritten to your spelling.
- **Shortcuts:** say "btw", get "by the way" (also catches "B.T.W.").
- **Import from another app:** copy the prompt for Claude, give it screenshots of the other app's
  lists, paste the code it writes into Import. Or read a screenshot on the phone (or share one to
  Voice Bubble). **Copy my code** exports everything for backup or another phone.

## Styles
Like your chats casual and your email formal? Pick a style per kind of app (main screen → Choose styles):
- **Formal.** Caps + punctuation (as Whisper writes it).
- **Casual:** caps, fewer commas, no final period, contractions ("I am" → "I'm").
- **very casual:** no caps, no punctuation except question marks.
- **Excited!** The last sentence ends with "!".

Categories: **Personal** (WhatsApp, Messages, Telegram, …), **Work** (Slack, Teams, …),
**Email** (Gmail, Outlook, …; greeting and sign-off go on their own lines) and **Other**.
To know which app you're in, the basic version needs **Usage access** (the Styles screen has a
button). Without it, everything uses the Other style.

## Notes
- Built for 64-bit ARM phones from roughly 2018 onwards (ARMv8.2).
- A sentence takes a few seconds to write out on *Accurate*, less on *Fast*.
- Recordings stop automatically after 5 minutes.
