# Raabta Keyboard

An original Android keyboard built with Kotlin and Android's real input-method APIs. Normal typing works offline.
The implementation covers **phase 0 and phase 1**, the **phase 2 customization foundation**,
plus user-requested **voice typing** ahead of phase 6,
of the [project requirements](docs/PROJECT_REQUIREMENTS.md).
Android **8.0 or newer** is required.

## Install and start typing

1. [Download APK 0.3.0 (settings, gestures, emoji and clipboard) directly](https://github.com/gityy34/keybord-ai-/raw/refs/heads/apk-keyboard-0.3.0/raabta-keyboard-0.3.0.apk), or obtain it from GitHub Actions below. Sign in to GitHub first if the repository is private.
2. Open the APK on your Android phone. If Android asks, allow your browser/file manager to install this app.
3. Open **Raabta Keyboard**.
4. Tap **1 · Enable Raabta**, find **Raabta Keyboard**, and turn it on. Android displays its standard keyboard-access warning. Menu names vary by manufacturer.
5. Return to the app. Tap **2 · Choose Raabta**, then select **Raabta Keyboard**.
6. Tap the test message field and type. Then test in WhatsApp or your notes app.

Use Shift once for one capital, twice (or hold it) for Caps Lock, and once more to turn Caps Lock off.
Use **?123** for numbers, **=\<** for extra symbols, and **ABC** to return to letters.
Hold Backspace to delete repeatedly. Enter becomes Search, Send, Next, or Done when the active app requests it.
Tap the globe to cycle English → Urdu → Roman Urdu; hold it for Android's keyboard picker. The gear opens category settings **inside the keyboard**, preserving your text field. Back returns through the settings pages, then hides the keyboard.

Test `hello world`, `Ab`, `ABC`, punctuation, numbers, deletion, and multiline Enter. Try airplane mode,
fast typing, switching apps, and reopening/rotating the keyboard. See the full [phone acceptance checklist](docs/ROADMAP.md).
The user reported successful basic phone testing on 2026-10-09. Extended app compatibility,
rotation, offline scenarios, and sustained daily use still need specific results.

## Customize your keyboard

Open the keyboard gear for category settings. Android’s keyboard settings entry and the launcher’s **Keyboard settings** button open the same category design. Enable/select/test onboarding remains separate. Choose:

- **Phone theme**, **Light**, or **Dark**; keyboard and setup colors follow the saved choice.
- **Compact**, **Normal**, or **Tall** height. Landscape uses shorter rows; wide screens center the keys.
- **Always show number row** to add digits above the letters.
- **Backspace hold delay** (200/400/600 ms) and **repeat speed** (55/85/120 ms); held Shift keeps Android’s native timing.
- **Vibrate on keypress** and **Sound on keypress** independently. Sound is off by default; feedback respects Android's haptic/touch-sound settings and silent mode. Missing hardware never interrupts typing.

In-keyboard settings update the view immediately; changes made from the launcher apply when the keyboard is shown again. Explicit personal dictionary entries and recent emoji choices are stored locally; ordinary typed content is not recorded. Keys use native press ripples;
held Backspace and held Shift remain supported. Changing the setup theme clears its disposable test fields.
Phone comfort, rotation and sustained typing still need real-device acceptance; no Gboard performance parity is claimed.

## Voice typing

The 0.3.0 download uses the same debug signing certificate as the delivered 0.1.0 APK and can update it
without uninstalling. Version 0.1.1 failed its voice test on the user’s OPPO Reno 11 5G (reported Android 16), with an unsupported-language/model error. The user subsequently reported online English/Urdu recognition working, with Urdu mistakes and short-pause completion issues. Version 0.2.1 added buffered Stop/long-pause completion; 0.3.0 retains those controls.

1. In **Voice typing** settings, choose **English voice**, **Urdu voice**, or **English + Urdu voice** (Android 14+, provider support required). Voice language and the **Languages** key-layout setting are independent.
2. Focus a normal text field and tap the keyboard's microphone. If permission is missing, tap **Allow microphone**,
   grant Android's permission, return to the text field, and tap the microphone again. Permission never starts recording by itself.
3. Speak while **Listening…** is shown. Recognition runs asynchronously and the live draft stays inside the keyboard. Short pauses and provider utterance endings do not insert it. Tap **■** to stop, or **×** to cancel and clear the draft.
4. Choose **Only when I press Stop**, **8 seconds of silence** (default), or **12 seconds of silence** in Voice typing settings. Stop gives the provider up to **0.8 seconds** to refine available text in Fast mode, or **3 seconds** in Prefer-final mode. If no draft is available, it waits up to 3 seconds; recognition or next-second insertion cannot be guaranteed without a provider result. A 5-minute session limit pauses recording and keeps its draft for explicit Stop/Cancel.
5. Buffered recognized text is inserted once at Stop or the chosen long silence. Ordinary typing, moving the cursor, hiding the keyboard,
   or switching fields/apps cancels the session and prevents late insertion into the wrong editor.

Voice is disabled in password fields, fields marked private by the host app, and non-text fields.
The installed system speech service is now the default, without forcing an offline model. It may process audio online.
**On-device only** is an optional voice switch for Android 12+ with a supported installed language model; leave it off for the initial retest.
English and Urdu availability depend on that service and its installed language models. Mixed mode requests automatic detection/switching between English and Urdu; the provider may not support or honor it. It is not app-level bilingual transcription, and older Android versions show a message instead of pretending to switch. Missing service,
unsupported language, denied permission, silence, network errors, and timeouts show a message and leave typing usable.
No typed context is sent, and the keyboard stores no recordings or transcript history.
For Urdu-heavy speech choose **Urdu voice**; mixed mode now defaults to mostly Urdu with high-precision switching. You can choose mostly English in Voice typing settings. Provider accuracy is not replaced by this change.
See [continuous dictation validation](docs/CONTINUOUS_VOICE_VALIDATION.md) and [reference review/next steps](docs/KEYBOARD_REFERENCE_REVIEW.md).

## Settings, suggestions and gestures (0.3.0)

Category pages use headings, descriptions, switches, sliders and radio choices. Gear navigation stays in the IME.
Preferences include key visibility, number rows, font/emoji size, height, one-handed/floating layout,
Space cursor sliding, punctuation shortcuts, held/flick symbols, key popups, sound volume/vibration duration,
Backspace timings and launcher-icon visibility. Tools includes theme/resize, emoji, text editing, share,
floating, undo and clipboard. Drag the title in floating mode; switching fields/hiding cancels held gestures.

- **Space:** tap for a space; slide left/right for cursor movement. Grapheme boundaries protect joined emoji
  and combining marks where extracted text is available; incompatible editors receive Android arrow events.
  Mixed-direction text and actual floating touch regions need phone acceptance.
- **Suggestions:** one stable strip shows local word completions, relevant emoji, common phrase next words,
  basic English grammar fixes and optional greeting replies. Buttons are reused rather than rebuilding the
  keyboard on every character. Emoji suggestions cover common English, Urdu and Roman Urdu keywords.
- **Corrections:** autocorrection is opt-in and handles known common typos on Space. Spell/grammar marking
  uses editor-supported spans. This is a small offline starter dictionary/rule set, not a full grammar engine.
- **Languages:** English, native Unicode Urdu and Roman Urdu QWERTY; Urdu held variants supply additional
  letters. Roman Urdu here remains Latin-script typing and suggestions, not automatic transliteration.
- **Emoji/media:** picker, optional fast-access/recent row, symbols row and adjustable size. Four original
  offline smile/heart sticker/GIF choices are included; the receiving app must support rich content. Online
  media search and translation/AI providers are not connected.
- **Clipboard:** explicit toolbar access, optional current-clipboard suggestions, basic extraction of phones,
  email/web and common street addresses. Previews expire after two minutes and are never written to disk.
  Sensitive clips and private/password fields block automatic previews. Pasted images require host support.
- **Optional access:** contact-name suggestions require Contacts permission and are queried on a worker.
  Screenshot previews require image permission and observe newly saved files named as screenshots while the
  keyboard is visible. Android 14 selected-photo access limits which images are available. Access is off by
  default; denial turns the setting off. This is not background screenshot capture or a persistent archive.
- **Dictionary/privacy:** save/remove words explicitly and clear dictionary/recent emoji. No automatic typing
  history, analytics, AI request or transcript storage. The app has no Internet permission. Generated local
  media templates alone use its cache. Contact names and clipboard previews stay in memory.
- **Glide:** optional exact dictionary-path matching is a starter implementation; it does not provide Gboard's
  probabilistic swipe recognition. Undo/redo and other editor actions depend on the receiving app.

See [the screenshot feature matrix and validation](docs/SETTINGS_0.3_VALIDATION.md). Device smoothness,
app-specific spans/paste/undo, gesture feel and Urdu layout comfort remain OPPO phone acceptance checks.
If a private GitHub download opens a page without downloading, use [the APK file page](https://github.com/gityy34/keybord-ai-/blob/apk-keyboard-0.3.0/raabta-keyboard-0.3.0.apk)
in Chrome, sign in with repository access and select **Download raw file / View raw**.

## Download future APKs from GitHub Actions

After these files are pushed to this repository:

1. Open [Actions](https://github.com/gityy34/keybord-ai-/actions).
2. Open the latest successful **Android debug APK** run for your branch.
3. Under **Artifacts**, download `raabta-keyboard-debug-<commit>`.
4. Unzip the download and install `app-debug.apk`. `app-debug.apk.sha256` is its checksum.

The workflow runs on pushes, pull requests, and manual dispatch. It installs Java 17 and Android SDK 35,
runs unit/service tests and Android lint, builds a debug APK, and uploads the APK and validation reports.
Actions versions were checked against their current official documentation during implementation.
Source code and the Gradle wrapper are tracked; APKs, caches, SDKs, signing keys, and local config are ignored in the development branch.
The separate `apk-phase-1-0.1.0`, `apk-voice-0.1.1`, and `apk-phase-2-0.2.0`, and `apk-voice-0.2.1`, `apk-keyboard-0.3.0` branches contain verified APKs as direct-download fallbacks while cloud access to
the Actions API is blocked. Future builds should use Actions artifacts or release assets.

Debug builds made on different machines may use different signing keys. If an update says “App not installed”,
uninstall the older debug build, install the new APK, and enable/select the keyboard again.
Use this debug APK for personal testing; release signing is a later task.

## Build locally

Install a full **JDK 17** and Android SDK **platform 35 / build-tools 35.0.0** using Android Studio or Android command-line tools.
Set `ANDROID_HOME` to your SDK, or put its location in the ignored `local.properties` file:

```properties
sdk.dir=/your/path/to/android-sdk
```

From the repository root:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
JUnit and Robolectric run regression tests using Android framework simulation; lint warnings fail the build.
The wrapper pins Gradle 8.11.1 and verifies the distribution's SHA-256.

For this Linux x86_64 Codex cloud machine (Python 3.12+, curl required):

```sh
python3 scripts/bootstrap-cloud.py
source scripts/cloud-env.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The bootstrap installs pinned, checksum-verified toolchains in `/workspace/toolchains`, accepts the Android SDK licenses,
uses the machine's trusted Java CA store, and keeps machine configuration outside the checkout.
Each cloud task is already isolated: use the existing checkout; do not create a Git worktree unless explicitly requested.

## Privacy and scope

The app has **no internet permission**, API key, AI SDK, analytics, clipboard history, or permanent typing capture.
Typing goes directly to the active app through `InputConnection`. Passwords are typed locally like other input;
ordinary typed text is not uploaded. Voice requests microphone permission only when explicitly requested;
the separate phone speech service may process audio online. Setup test fields are not saved and are cleared when the screen closes.
Android cloud backup and device transfer of app files are excluded.

English QWERTY, numbers/symbols, editor actions, setup, and core typing are implemented. Basic system-aware colors
and held Backspace are included as foundation conveniences. English/Urdu dictation is implemented using Android speech recognition.
Urdu/Roman Urdu key layouts, prediction, emoji/clipboard, AI and translation remain in their agreed [later phases](docs/ROADMAP.md).
Theme, height, number-row and sound/haptic controls are implemented; broader customization remains a future goal.

Architecture and all future requirements are documented in `docs/`. Validation evidence and known limits are in
[VALIDATION.md](docs/VALIDATION.md).
