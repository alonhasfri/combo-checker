# Comboed (combo-checker)

Android app that turns real tasks (homework, essays, organizing, video editing, custom) into a
game: you show proof, an AI checks it, you earn points, coins, combos and bonuses.

Full history, decisions and to-dos: `memory/MEMORY-BOOK.md` in the private repo
`alonhasfri/shared-project` (section 1).

## Key facts

- Kotlin, UI built in code (`Ui.kt` kit, `ui_*.xml` vector icons), plus a WebView game screen
  `assets/combo.html`. Hebrew + English through `Lang.t`, with right-to-left support.
- **The Android source is `combo-android.zip`.** Unzip, edit, re-zip, and commit the zip only
  (never the unzipped `combo-android/` folder). CI unzips it. `combo-desktop/` is the Electron
  desktop version (manual "Build Windows" workflow).
- Two editions (product flavors `safe` / `full`, `BuildConfig.FULL`):
  - `app/src/full`: prank code (Pranks, Ads, LoginWall, Crash, Voice, PrankActivity, `prank.html`).
  - `app/src/safe`: do-nothing stand-ins with the same members, plus a real Terms page.
  - Never put prank code in `app/src/main` or `app/src/safe`. Every full-only member used by shared
    code needs a stand-in in `safe`, or the public build breaks.
- CI `.github/workflows/build.yml` builds signed **release** APKs (keystore from a GitHub secret,
  never committed). `versionCode` = run number. Only `main` builds are "latest"; branch builds are
  pre-releases.
- Download links: safe `releases/latest/download/combo-checker.apk`, full
  `releases/latest/download/comboed-full.apk`. The in-app update check reads "latest", so never mark
  any other app's release as latest here.
- Free to run: Firebase Spark (project `comboed`: anonymous auth, Firestore, Crashlytics), no hosted
  AI key (users bring a free OpenRouter or Gemini key). The AI key and parent PIN are never uploaded.
- Changes to Firestore rules have to be republished by hand in the Firebase console. Say so when
  you change them.

## Standing rules

- Every new feature gets an entry on the Developer tests screen.
- Teacher/owner controls open only through Teacher mode or owner lists. Profiles on one phone share
  one Firebase id.
- Keep Firebase Analytics off (a parent decides). Keep the privacy/Terms text accurate.
- Voice Bubble moved to its own repo (`alonhasfri/voice-bubble`). This repo is Comboed only.
